"""A local stand-in for Anthropic's Messages API, for trying the AI reading screens in the demo
without a key or cost. It is not the real service: a credit card statement request gets last
month's lines of the English demo's TD Visa (plus one line the books lack), and every other
request the same receipt answer.

    python tools/dev/fake_anthropic.py [port] [folder for the received pictures]
    ./gradlew :app:desktop:runDemo -Psection=AI -PaiUrl=http://127.0.0.1:8765

In the demo, turn AI reading on and save any text as the key (the demo keeps it in memory only).
"""
import base64
import datetime
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


def card_statement():
    """Last month's purchases of the English demo's TD Visa, as its statement prints them."""
    first = (datetime.date.today().replace(day=1) - datetime.timedelta(days=1)).replace(day=1)
    lines = [(4, "PET VALU #221", 74.99), (6, "LOBLAWS #1234", 187.32), (8, "PETRO-CANADA", 68.55), (10, "PAYMENT - THANK YOU", -566.57),
             (13, "COSTCO WHOLESALE", 243.90), (17, "NETFLIX.COM", 18.99), (21, "RIVERSIDE DINER", 64.15), (24, "PETRO-CANADA", 64.95)]
    previous = 566.57
    new = round(previous + sum(a for _, _, a in lines), 2)
    return {
        "issuer": "TD", "card_last4": "1234", "period_start": first.isoformat(), "period_end": first.replace(day=28).isoformat(),
        "previous_balance": previous, "new_balance": new, "minimum_payment": 10.00, "currency": "CAD",
        "transactions": [{"date": first.replace(day=d).isoformat(), "description": t, "amount": a} for d, t, a in lines],
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
        properties = body.get("output_config", {}).get("format", {}).get("schema", {}).get("properties", {})
        answer = card_statement() if "new_balance" in properties else RECEIPT
        print("request:", len(images), "page(s),", body.get("model"), "effort", body.get("output_config", {}).get("effort"),
              "->", "card statement" if answer is not RECEIPT else "receipt", flush=True)
        self._send(200, {
            "id": "msg_stand_in", "type": "message", "role": "assistant", "model": body.get("model"),
            "content": [{"type": "text", "text": json.dumps(answer)}], "stop_reason": "end_turn", "stop_sequence": None,
            "usage": {"input_tokens": 1650 * len(images) + 1500, "output_tokens": 410},
        })

    def log_message(self, *args):
        pass


print("stand-in for the Messages API on http://127.0.0.1:%d" % PORT, flush=True)
HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
