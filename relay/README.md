# CraftNode public UDP relay

This directory contains an example FRP server configuration for forwarding Bedrock UDP traffic to a CraftNode host behind NAT/CGNAT. It does **not** provision a server or make a public endpoint by itself.

## Requirements

- A Linux host with a stable, publicly reachable IPv4 address (or a domain pointing to it).
- Inbound **TCP 7000** for the FRP control connection.
- Inbound **UDP 19132** for Bedrock traffic.
- A host/provider that explicitly supports public UDP ingress and outbound traffic. A generic web app host or TCP-only proxy is not sufficient.
- FRP server version matching the FRP client bundled by CraftNode (currently configured for v0.71.1).

A free host is suitable only if it supports persistent processes and public UDP on these ports without unexpected sleep, bandwidth limits, or charges. Check the provider's current limits before deploying.

## Configure

1. Install the matching upstream FRP release on the Linux host. Put the `frps` executable at `/usr/local/bin/frps`.
2. Create a dedicated service account and directories, for example:
   ```sh
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin frp
   sudo install -d -o root -g frp -m 0750 /etc/frp
   sudo install -d -o frp -g frp -m 0750 /var/log/frp
   ```
3. Copy `frps.toml.example` to `/etc/frp/frps.toml`, then replace the example token with a long, unique random secret. Do not commit or share the real token.
4. Restrict access to the configuration:
   ```sh
   sudo chown root:frp /etc/frp/frps.toml
   sudo chmod 0640 /etc/frp/frps.toml
   ```
5. Review `frps.service.example`. Copy it to `/etc/systemd/system/frps.service` if the paths match your installation.
6. Open TCP 7000 and UDP 19132 in both the host firewall and provider firewall/security group. Do not expose an FRP dashboard publicly.
7. Start the service:
   ```sh
   sudo systemctl daemon-reload
   sudo systemctl enable --now frps
   sudo systemctl status frps
   ```

## Connect CraftNode

In CraftNode's Remote screen, enter the relay's public IP/domain, control port `7000`, the same secret token, public UDP port `19132`, and the local Bedrock server port (normally `19132`, if the running engine actually listens there).

FRP must be running on the relay before the phone starts its tunnel. The host firewall and provider firewall must both permit the traffic.

## Security and verification

- Keep the token private; anyone with it may be able to authenticate to the relay.
- Keep the allowed public port list narrow. If you change the public UDP port, update `allowPorts`, both firewalls, and CraftNode's public-port field consistently.
- Do not use a relay you do not control or trust.
- A successful FRP process start is not proof that Bedrock works. Test from a different network (for example, a friend's connection) and join using the public IP/domain and UDP port.
- This configuration is a deployment template only. No relay is hosted or paid for by this repository, and no end-to-end Bedrock connection has been confirmed by adding these files.
