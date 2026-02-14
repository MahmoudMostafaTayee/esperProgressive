import socket
import json
import sys
import time

def listen_to_table(host='localhost', port=9999):
    print(f"Connecting to Global ID Table at {host}:{port}...", flush=True)
    
    s = None
    connected = False
    while not connected:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(1.0) # Allow periodic checks for KeyboardInterrupt
            s.connect((host, port))
            connected = True
            print("Connected! Waiting for updates (Press Ctrl+C to stop)...\n", flush=True)
        except ConnectionRefusedError:
            print(f"Waiting for Java app to start on {host}:{port}...", flush=True)
            if s: s.close()
            time.sleep(2)
        except KeyboardInterrupt:
            print("\nStopped by user.")
            if s: s.close()
            return
        except Exception as e:
            print(f"Connection error: {e}")
            if s: s.close()
            time.sleep(2)

    buffer = ""
    try:
        while True:
            try:
                data = s.recv(4096).decode('utf-8')
                if not data:
                    print("\nConnection closed by server.", flush=True)
                    break
                
                buffer += data
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    if not line.strip():
                        continue
                        
                    try:
                        rows = json.loads(line)
                        print("-" * 50, flush=True)
                        print(f"{'GlobalID':<10} | {'LastSeen':<15} | {'Attributes'}", flush=True)
                        print("-" * 50, flush=True)
                        for row in rows:
                            gid = row.get('globalId')
                            last_seen = row.get('lastSeen')
                            attrs = row.get('attributes', {})
                            display_attrs = {k: v for k, v in attrs.items() if k not in ['Feature', 'Keypoints']}
                            print(f"{gid:<10} | {last_seen:<15} | {display_attrs}", flush=True)
                        print("-" * 50 + "\n", flush=True)
                    except json.JSONDecodeError:
                        pass
            except socket.timeout:
                continue # Normal timeout, just loop back and check for KeyboardInterrupt
                
    except KeyboardInterrupt:
        print("\nStopping listener gently...", flush=True)
    except Exception as e:
        print(f"An error occurred: {e}", flush=True)
    finally:
        s.close()

if __name__ == "__main__":
    listen_to_table()
