"""
Socket patcher for selective SOCKS5 proxy routing.

This module patches Python's socket module to route traffic through a SOCKS5 proxy
based on the destination IP address. Specifically:
- IP addresses starting with '100.' (Tailscale network) are routed through the proxy
- All other IP addresses use direct connections

This is designed for running Celery workers in unprivileged containers with
Tailscale userspace-networking.
"""

import socket as _socket
import socks
import ipaddress
import os
from typing import Tuple, Optional


class SelectiveProxySocket(_socket.socket):
    """
    A socket wrapper that selectively routes traffic through a SOCKS5 proxy
    based on the destination IP address.
    """

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._proxy_enabled = False
        self._original_socket = True

    def connect(self, address):
        """
        Override connect to check if we should use SOCKS5 proxy.

        Args:
            address: Tuple of (host, port) or string path for Unix socket
        """
        # Handle Unix sockets and non-tuple addresses
        if not isinstance(address, tuple) or len(address) < 2:
            return super().connect(address)

        host, port = address[0], address[1]

        # Check if we should proxy this connection
        should_proxy = self._should_use_proxy(host)

        if should_proxy:
            # Get proxy settings from environment
            proxy_host = os.environ.get('SOCKS5_PROXY_HOST', 'localhost')
            proxy_port = int(os.environ.get('SOCKS5_PROXY_PORT', '1055'))

            # Convert this socket to a SOCKS5 socket
            self.setsockopt(_socket.SOL_SOCKET, _socket.SO_KEEPALIVE, 1)

            # Create a new SOCKS5 socket with the same family and type
            socks_socket = socks.socksocket(self.family, self.type, self.proto)
            socks_socket.set_proxy(
                proxy_type=socks.SOCKS5,
                addr=proxy_host,
                port=proxy_port
            )

            # Copy socket options from original socket
            try:
                # Preserve timeout settings
                timeout = self.gettimeout()
                if timeout is not None:
                    socks_socket.settimeout(timeout)
            except Exception:
                pass

            # Replace our socket's file descriptor with the SOCKS socket
            # This is a bit of a hack, but it works
            self.close()
            self.__class__ = socks_socket.__class__
            self.__dict__ = socks_socket.__dict__

            # Now connect through the SOCKS proxy
            return super(SelectiveProxySocket, self).connect((host, port))
        else:
            # Direct connection
            return super().connect(address)

    def _should_use_proxy(self, host: str) -> bool:
        """
        Determine if the connection should use the SOCKS5 proxy.

        Args:
            host: The destination hostname or IP address

        Returns:
            True if the connection should use the proxy, False otherwise
        """
        try:
            # Try to parse as IP address
            ip = ipaddress.ip_address(host)
            ip_str = str(ip)

            # Check if it's a Tailscale IP (100.x.x.x)
            if ip_str.startswith('100.'):
                return True

            return False
        except ValueError:
            # Not a valid IP address, might be a hostname
            # We need to resolve it first
            try:
                # Use the original socket.getaddrinfo to resolve
                resolved = _socket.getaddrinfo(host, None, _socket.AF_UNSPEC, _socket.SOCK_STREAM)
                if resolved:
                    # Check the first resolved IP
                    ip_str = resolved[0][4][0]
                    if ip_str.startswith('100.'):
                        return True
            except Exception:
                # If resolution fails, don't use proxy
                pass

            return False


# Keep a reference to the original socket class
_original_socket_class = _socket.socket


def patch_socket():
    """
    Patch the socket module to use SelectiveProxySocket for all new sockets.

    This should be called early in the application startup, before any network
    connections are made.
    """
    _socket.socket = SelectiveProxySocket
    print("[socket_patcher] Socket patching enabled: 100.* IPs will use SOCKS5 proxy")


def unpatch_socket():
    """
    Restore the original socket implementation.

    This is mainly useful for testing.
    """
    _socket.socket = _original_socket_class
    print("[socket_patcher] Socket patching disabled")


def is_patched() -> bool:
    """
    Check if the socket module is currently patched.

    Returns:
        True if patched, False otherwise
    """
    return _socket.socket is SelectiveProxySocket
