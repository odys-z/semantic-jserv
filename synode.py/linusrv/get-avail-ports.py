import socket

def report_port_ranges(start_port, end_port, host="127.0.0.1"):
    """Scans ports and prints beautifully formatted blocks of available/used ranges."""
    print("Available ports:")
    
    current_status = None
    range_start = start_port

    for port in range(start_port, end_port + 1):
        # Check port availability
        is_available = False
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                s.bind((host, port))
                is_available = True
            except OSError:
                pass

        status = "available" if is_available else "used"

        # Initialize state on the first item
        if current_status is None:
            current_status = status

        # State changed: print the range we just completed
        if status != current_status:
            print_formatted_range(current_status, range_start, port - 1)
            range_start = port
            current_status = status

    # Print the last remaining range
    print_formatted_range(current_status, range_start, end_port)

def print_formatted_range(status, start, end):
    """Prints ranges formatted exactly as requested."""
    icon = "✅" if status == "available" else "❌"
    
    if start == end:
        print(f"{icon}  [{start}]")
    else:
        print(f"{icon}  [{start} - {end}]")

if __name__ == "__main__":
    # Scan from 1024 to 65535
    report_port_ranges(1024, 65535)

