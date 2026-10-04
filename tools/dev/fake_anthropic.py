"""A local stand-in for Anthropic's Messages API, for trying the AI reading screens in the demo
without a key or cost. It is not the real service: every reading gets the same receipt answer.

    python tools/dev/fake_anthropic.py [port] [folder for the received pictures]
    ./gradlew :app:desktop:runDemo -Psection=AI -PaiUrl=http://127.0.0.1:8765

In the demo, turn AI reading on and save any text as the key (the demo keeps it in memory only).
"""
import base64
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
SAVE = sys.argv[2] if len(sys.argv) > 2 else None

RECEIPT = {
    "merchant": "Loblaws", "merchant_address": "1250 Main St W", "date": "2026-09-06", "currency": "CAD",
    "line_items": [
        {"description": "MILK 2% 4L", "amount": 6.49}, {"description": "WHOLE CHICKEN", "amount": 17.98},
        {"description": "PRODUCE", "amount": 42.37}, {"description": "GROCERY", "amount": 112.59},
    ],
    "subtotal": 179.43, "taxes": [{"name": "HST", "amount": 7.89}], "total": 187.32,
    "payment_method": "credit", "card_last4": "1234",
}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        # The key check lists the models.
        if self.path.startswith("/v1/models"):
            self._send(200, {"data": [{"type": "model", "id": "claude-opus-5-5", "display_name": "Claude Opus 5.5", "created_at": "2026-01-01T00:00:00Z"}],
                             "has_more": False, "first_id": "claude-opus-5-5", "last_id": "claude-opus-5-5"})
        else:
            self._send(404, {"type": "error", "error": {"type": "not_found_error", "message": "not found"}})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["content-length"])))
        images = [c for c in body["messages"][0]["content"] if c["type"] == "image"]
        if SAVE:
            os.makedirs(SAVE, exist_ok=True)
            for i, img in enumerate(images):
                with open(os.path.join(SAVE, "sent-%d.jpg" % i), "wb") as f:
                    f.write(base64.b64decode(img["source"]["data"]))
        print("request:", len(images), "page(s),", body.get("model"), "effort", body.get("output_config", {}).get("effort"), flush=True)
        self._send(200, {
            "id": "msg_stand_in", "type": "message", "role": "assistant", "model": body.get("model"),
            "content": [{"type": "text", "text": json.dumps(RECEIPT)}], "stop_reason": "end_turn", "stop_sequence": None,
            "usage": {"input_tokens": 1650 * len(images) + 1500, "output_tokens": 410},
        })

    def log_message(self, *args):
        pass


print("stand-in for the Messages API on http://127.0.0.1:%d" % PORT, flush=True)
HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
