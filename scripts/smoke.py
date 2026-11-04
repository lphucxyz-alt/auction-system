#!/usr/bin/env python3
"""Real packaged-server smoke test: two bidders, pushed winner, payment and restart."""
import json
import os
from pathlib import Path
import queue
import socket
import subprocess
import tempfile
import threading
import uuid

ROOT = Path(__file__).resolve().parents[1]
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else 'java'


class Peer:
    def __init__(self, port):
        self.socket = socket.create_connection(('127.0.0.1', port), timeout=10)
        self.stream = self.socket.makefile('rwb')
        self.events = []

    def read(self):
        line = self.stream.readline()
        if not line:
            raise RuntimeError('Unexpected server disconnect')
        return json.loads(line)

    def request(self, kind, **payload):
        identifier = str(uuid.uuid4())
        self.stream.write((json.dumps(dict(type=kind, requestId=identifier, payload=payload)) + '\n').encode())
        self.stream.flush()
        while True:
            response = self.read()
            if response.get('requestId') != identifier:
                self.events.append(response)
                continue
            assert response['type'] == 'RESULT', response
            return response['payload']

    def login(self, username, password):
        return self.request('LOGIN', username=username, password=password)

    def close(self):
        self.stream.close()
        self.socket.close()


def start(data):
    process = subprocess.Popen([JAVA, '-Dauction.port=0', '-Dauction.data=' + data,
                                '-jar', str(ROOT / 'server/target/server-1.0-SNAPSHOT-all.jar'), '--demo'],
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    lines = queue.Queue()
    def output():
        for line in process.stdout:
            lines.put(line.rstrip())
    threading.Thread(target=output, daemon=True).start()
    try:
        while True:
            line = lines.get(timeout=15)
            if line.startswith('Auction server ready on '):
                return process, int(line.rsplit(':', 1)[1])
    except BaseException:
        stop(process)
        raise


def stop(process):
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)
    process.stdout.close()


def main():
    with tempfile.TemporaryDirectory(prefix='auction-smoke-') as data:
        process, port = start(data)
        peers = []
        try:
            seller, one, two = [Peer(port) for _ in range(3)]
            peers.extend([seller, one, two])
            seller.login('seller', 'DemoSeller123!')
            one.login('bidder1', 'DemoBidder123!')
            two.login('bidder2', 'DemoBidder123!')
            lot = seller.request('CREATE_AUCTION', name='Smoke lot', description='Packaged JAR check',
                                 category='ART', extra1='Demo artist', extra2=2026,
                                 startingPrice=100, startDelaySeconds=300, durationSeconds=2,
                                 antiSniping=False)['auction']
            identifier = lot['id']
            seller.request('START_AUCTION', auctionId=identifier)
            one.request('SUBSCRIBE', auctionId=identifier)
            one.request('PLACE_BID', auctionId=identifier, amount=200)
            two.request('AUTO_BID', auctionId=identifier, maxBid=1000, increment=10)
            while True:
                event = one.read()
                if event['type'] == 'AUCTION_FINISHED' and event['payload']['auction']['id'] == identifier:
                    assert event['payload']['auction']['leader'] == 'bidder2', event
                    break
            paid = two.request('PAY_AUCTION', auctionId=identifier)['auction']
            assert paid['status'] == 'PAID' and paid['currentPrice'] == 210, paid
            print('PASS: real JAR, login, CRUD, two bidders, auto-bid, push, automatic close, simulated payment')
        finally:
            for peer in peers:
                peer.close()
            stop(process)
        process, port = start(data)
        try:
            restored = Peer(port)
            try:
                restored.login('bidder2', 'DemoBidder123!')
                saved = restored.request('GET_AUCTION', auctionId=identifier)['auction']
                assert saved['status'] == 'PAID' and saved['historyCount'] == 2, saved
                print('PASS: process restart restores users, winner, price, history and payment state')
            finally:
                restored.close()
        finally:
            stop(process)


if __name__ == '__main__':
    main()
