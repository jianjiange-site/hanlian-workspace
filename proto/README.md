# proto

Interface contracts for Hanlian workspace.

This directory contains minimal gRPC proto definitions for:

- user
- im
- match
- post
- payment

Current goal:

- keep service boundaries clear
- define minimal Ping RPCs
- prepare for later Java/Python package generation
- publish to Nexus after credentials are available

Rules:

- business services must not copy .proto files into their source trees
- business services should consume generated packages
- versions are tracked by each module VERSION file
