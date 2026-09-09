#!/usr/bin/env python3
"""Generate Dozzle credentials from Compose's resolved deployment environment."""

import json
import os
from pathlib import Path
import subprocess
import tempfile


def run(command, input_text=None):
    result = subprocess.run(command, input=input_text, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, universal_newlines=True)
    if result.returncode:
        raise SystemExit("Log viewer credential initialization failed; check Docker and deployment configuration.")
    return result.stdout


def main():
    logs_dir = Path(__file__).resolve().parent
    deploy_dir = logs_dir.parent
    config = json.loads(run([
        "docker", "compose", "--env-file", str(deploy_dir / ".env"),
        "-f", str(deploy_dir / "docker-compose.yml"), "config", "--format", "json",
    ]))
    environment = config["services"]["arvello-backend"]["environment"]
    username = environment.get("LOG_VIEWER_USERNAME") or environment.get("ADMIN_USERNAME")
    password = environment.get("LOG_VIEWER_PASSWORD") or environment.get("ADMIN_PASSWORD")
    if not username or not password or "\n" in password or "\r" in password:
        raise SystemExit("Set non-empty, single-line log viewer or admin credentials in the deployment environment.")
    viewer = json.loads(run([
        "docker", "compose", "-f", str(logs_dir / "compose.yml"),
        "config", "--format", "json",
    ]))
    image = viewer["services"]["dozzle"]["image"]
    users = run([
        "docker", "run", "--rm", "-i", "--network", "none", "--memory", "128m",
        "--env", "DOZZLE_NO_ANALYTICS=true", "--entrypoint", "/dozzle", image,
        "generate", username, "--name", "ARVELLO", "--user-filter", "name=arvello-backend",
    ], password + "\n")
    # Replace atomically; deployments recreate Dozzle to remount the new file.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=str(logs_dir),
                                         prefix=".users-", delete=False) as output:
            temporary = output.name
            os.chmod(temporary, 0o600)
            output.write(users)
        os.replace(temporary, str(logs_dir / "users.yml"))
    finally:
        if temporary and os.path.exists(temporary):
            os.unlink(temporary)
    print("Log viewer credentials initialized; no plaintext password was written.")


if __name__ == "__main__":
    main()
