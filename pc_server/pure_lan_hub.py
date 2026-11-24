import functools
import http.server
import os
import re
import socket
import socketserver
import subprocess

PORT = 8000
WEB_DIR = os.path.dirname(os.path.abspath(__file__))


def get_all_local_ips():
    found = []
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and not ip.startswith("169.254.") and ip not in found:
                found.append(ip)
    except Exception:
        pass

    try:
        out = subprocess.check_output(["ipconfig"], text=True, errors="ignore")
        for ip in re.findall(r"IPv4 Address[.\s]*:\s*([0-9.]+)", out):
            if not ip.startswith("127.") and not ip.startswith("169.254.") and ip not in found:
                found.append(ip)
    except Exception:
        pass

    return found


class LocalOnlyHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        client_ip = self.client_address[0]
        print(f"[LAN {client_ip}] {format % args}")


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


if __name__ == "__main__":
    handler = functools.partial(LocalOnlyHandler, directory=WEB_DIR)
    ips = get_all_local_ips()

    with ThreadingTCPServer(("0.0.0.0", PORT), handler) as httpd:
        print("=" * 56)
        print("PURE LOCAL MULTI-IP SERVER RUNNING (NO 3RD PARTY)")
        print(f"Folder    : {WEB_DIR}")
        print(f"Loopback  : http://127.0.0.1:{PORT}")
        for ip in ips:
            print(f"Direct IP : http://{ip}:{PORT}")
        print("=" * 56)
        print("Stop with Ctrl+C")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped.")
