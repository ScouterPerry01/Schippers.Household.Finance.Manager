"""A local stand-in for Anthropic's Messages API, for trying the AI reading screens in the demo
without a key or cost. It is not the real service: a credit card statement request gets last
month's lines of the English demo's TD Visa (plus one line the books lack), a pay stub request
the English demo's pay, a trade confirmation or investment statement request a purchase in the
English demo's TFSA, a receipt or invoice request the same receipt answer, and a type added in the
folder an answer made up from its schema.

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
        {"description": "MILK 2% 4L", "amount": 6.49, "taxes": []}, {"description": "WHOLE CHICKEN", "amount": 17.98, "taxes": []},
        {"description": "PRODUCE", "amount": 42.37, "taxes": []}, {"description": "GROCERY", "amount": 51.90, "taxes": []},
        {"description": "HOUSEHOLD", "amount": 60.69, "taxes": ["HST"], "tax_codes": "H"},
    ],
    "subtotal": 179.43, "taxes": [{"name": "HST", "amount": 7.89}], "total": 187.32,
    "payment_method": "credit", "card_last4": "1234",
}

PAY_STUB = {
    "employer": "Employer Inc.", "employee": "Alex", "pay_date": datetime.date.today().replace(day=1).isoformat(), "currency": "CAD",
    "earnings": [{"description": "Regular pay", "hours": 75, "amount": 4315.00}], "gross_pay": 4315.00,
    "deductions": [
        {"kind": "income_tax_federal", "description": "Federal tax", "amount": 520.00},
        {"kind": "income_tax_provincial", "description": "Ontario tax", "amount": 280.00},
        {"kind": "cpp", "description": "CPP", "amount": 245.00}, {"kind": "ei", "description": "EI", "amount": 72.00},
        {"kind": "union_dues", "description": "Union dues", "amount": 38.00}, {"kind": "other", "description": "United Way", "amount": 10.00},
    ],
    "net_pay": 3150.00, "year_to_date_gross": 81985.00,
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


def trade_confirmation():
    """A purchase of 20 XEQT in the English demo's TFSA, three days ago, settled the next day."""
    trade = datetime.date.today() - datetime.timedelta(days=3)
    return {
        "institution": "TD Direct Investing", "account_number_last_digits": "5512", "account_type": "TFSA",
        "confirmation_number": "T-20481", "currency": "CAD",
        "trades": [{"action": "buy", "trade_date": trade.isoformat(), "settlement_date": (trade + datetime.timedelta(days=1)).isoformat(),
                    "symbol": "XEQT", "description": "iShares Core Equity ETF Portfolio", "quantity": 20, "price": 34.55,
                    "gross_amount": 691.00, "commission": 0.00, "net_amount": 691.00}],
    }


def investment_statement():
    """This month's statement of the English demo's TFSA, with the purchase above (matched, not added twice)."""
    today = datetime.date.today()
    trade = today - datetime.timedelta(days=3)
    return {
        "institution": "TD Direct Investing", "account_number_last_digits": "5512", "account_type": "TFSA",
        "period_start": today.replace(day=1).isoformat(), "period_end": today.isoformat(), "currency": "CAD",
        "opening_cash_balance": 1521.00, "cash_balance": 830.00, "total_value": 17887.80,
        "holdings": [{"symbol": "XEQT", "description": "iShares Core Equity ETF Portfolio", "quantity": 493, "price": 34.60, "market_value": 17057.80}],
        "activity": [{"date": trade.isoformat(), "type": "buy", "symbol": "XEQT", "description": "Bought XEQT",
                      "quantity": 20, "price": 34.55, "commission": 0.00, "amount": -691.00}],
    }


def sample(schema, name=""):
    """An answer that follows any schema, for a type added in the folder (AI-03)."""
    t = schema.get("type")
    if "enum" in schema:
        return schema["enum"][0]
    if t == "object":
        return {k: sample(v, k) for k, v in schema.get("properties", {}).items()}
    if t == "array":
        return [sample(schema.get("items", {}), name)]
    if t == "number":
        return 123.45
    if t == "integer":
        return 1
    if t == "boolean":
        return True
    if schema.get("format") == "date":
        return datetime.date.today().isoformat()
    return "CAD" if name == "currency" else "Sample " + name.replace("_", " ")


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
        schema = body.get("output_config", {}).get("format", {}).get("schema", {})
        properties = schema.get("properties", {})
        kind = ("card statement" if "new_balance" in properties else "pay stub" if "net_pay" in properties
                else "trade confirmation" if "trades" in properties else "investment statement" if "holdings" in properties
                else "receipt" if "line_items" in properties else "other")
        answer = {"card statement": card_statement, "pay stub": lambda: PAY_STUB, "receipt": lambda: RECEIPT,
                  "trade confirmation": trade_confirmation, "investment statement": investment_statement,
                  "other": lambda: sample(schema)}[kind]()
        print("request:", len(images), "page(s),", body.get("model"), "effort", body.get("output_config", {}).get("effort"), "->", kind, flush=True)
        self._send(200, {
            "id": "msg_stand_in", "type": "message", "role": "assistant", "model": body.get("model"),
            "content": [{"type": "text", "text": json.dumps(answer)}], "stop_reason": "end_turn", "stop_sequence": None,
            "usage": {"input_tokens": 1650 * len(images) + 1500, "output_tokens": 410},
        })

    def log_message(self, *args):
        pass


print("stand-in for the Messages API on http://127.0.0.1:%d" % PORT, flush=True)
HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
