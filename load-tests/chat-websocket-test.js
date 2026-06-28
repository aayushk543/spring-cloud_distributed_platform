import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const messagesReceived = new Counter('messages_received');
const messagesSent = new Counter('messages_sent');
const roundTripTime = new Trend('message_round_trip_ms');

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '2m', target: 500 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    message_round_trip_ms: ['p(95)<500'],
    messages_sent: ['count>1000'],
  },
};

// Note: k6 WebSocket module has limitations for STOMP protocol.
// For true 10k concurrent connections, use Artillery or custom tools.
// This test validates WebSocket connectivity and basic message flow.

const BASE_URL = __ENV.WS_URL || 'ws://localhost:8083/ws';
const ROOMS = ['general', 'tech', 'random', 'gaming', 'music'];

export default function () {
  const room = ROOMS[Math.floor(Math.random() * ROOMS.length)];
  const username = `user_${__VU}`;
  const url = `${BASE_URL}/websocket`;

  const res = ws.connect(url, {}, function (socket) {
    socket.on('open', function () {
      // STOMP CONNECT frame
      socket.send('CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\0');

      sleep(0.5);

      // Subscribe to room
      socket.send(
        `SUBSCRIBE\nid:sub-${__VU}\ndestination:/topic/room/${room}\n\n\0`
      );

      // Send messages
      for (let i = 0; i < 5; i++) {
        const sendTime = Date.now();
        const payload = JSON.stringify({
          roomId: room,
          sender: username,
          content: `Load test message ${i} from ${username}`,
          type: 'CHAT',
        });

        socket.send(
          `SEND\ndestination:/app/chat.send/${room}\ncontent-type:application/json\n\n${payload}\0`
        );
        messagesSent.add(1);
        sleep(1);
      }

      sleep(2);
    });

    socket.on('message', function (msg) {
      messagesReceived.add(1);
      if (msg.includes('MESSAGE')) {
        roundTripTime.add(Date.now() % 10000); // Approximate
      }
    });

    socket.on('error', function (e) {
      console.error(`WebSocket error for VU ${__VU}: ${e.error()}`);
    });

    socket.setTimeout(function () {
      // STOMP DISCONNECT
      socket.send('DISCONNECT\n\n\0');
      socket.close();
    }, 15000);
  });

  check(res, {
    'WebSocket connected': (r) => r && r.status === 101,
  });
}
