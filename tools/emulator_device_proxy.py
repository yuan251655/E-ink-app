"""Android-emulator bridge for the PhotoPainter device.

The emulator reaches the Windows host at 10.0.2.2; this proxy then forwards
each request to the device on the current STA LAN address.  It deliberately
uses Connection: close because the ESP HTTP server is single-client sensitive.
"""

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from os import environ
from pathlib import Path
from threading import Lock
from time import monotonic
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

TARGET = environ.get("EINK_DEVICE_URL", "http://192.168.145.12").rstrip("/")
PORT = int(environ.get("EINK_PROXY_PORT", "8080"))
NO_PROXY = build_opener(ProxyHandler({}))
LOG_PATH = Path(environ.get("EINK_PROXY_LOG", Path(__file__).with_name("emulator_device_proxy.log")))
# The ESP server handles one request reliably at a time. Serialize bridge
# forwarding so concurrent heartbeats and media reads cannot clog it.
DEVICE_REQUEST_LOCK = Lock()


def log(message):
    LOG_PATH.parent.mkdir(parents=True, exist_ok=True)
    with LOG_PATH.open("a", encoding="utf-8") as file:
        file.write(message + "\n")


class Forwarder(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _forward(self):
        started = monotonic()
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length) if length else None
            request = Request(f"{TARGET}{self.path}", data=body, method=self.command)
            for name, value in self.headers.items():
                if name.lower() not in {"host", "content-length", "connection"}:
                    request.add_header(name, value)
            request.add_header("Connection", "close")
            with DEVICE_REQUEST_LOCK, NO_PROXY.open(request, timeout=130) as response:
                data = response.read()
                self._reply(response.status, response.headers.items(), data)
                log(f"{self.command} {self.path} -> {response.status} {len(data)}B {monotonic() - started:.3f}s")
        except HTTPError as error:
            data = error.read()
            self._reply(error.code, error.headers.items(), data)
            log(f"{self.command} {self.path} -> {error.code} {len(data)}B {monotonic() - started:.3f}s")
        except (URLError, TimeoutError) as error:
            self._reply(502, (), f"Device gateway unavailable: {error}".encode())
            log(f"{self.command} {self.path} -> 502 {monotonic() - started:.3f}s {error}")
        except Exception as error:  # keep the bridge alive after one bad request
            self._reply(500, (), f"Proxy error: {error}".encode())
            log(f"{self.command} {self.path} -> 500 {monotonic() - started:.3f}s {error}")

    def _reply(self, status, headers, data):
        self.send_response(status)
        for name, value in headers:
            if name.lower() not in {"transfer-encoding", "connection", "content-encoding", "content-length"}:
                self.send_header(name, value)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self): self._forward()
    def do_POST(self): self._forward()
    def do_DELETE(self): self._forward()


if __name__ == "__main__":
    print(f"Emulator bridge: http://0.0.0.0:{PORT} -> {TARGET}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Forwarder).serve_forever()
