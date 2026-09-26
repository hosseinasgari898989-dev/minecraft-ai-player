# Path B: llama.cpp RPC + GitHub Codespaces

This branch prepares a Codespace as the remote ggml RPC device for a local Android/Termux llama.cpp main host.

## Remote Codespace
1. Create a Codespace from this branch.
2. Wait for the post-create script to finish.
3. Start the remote RPC server:
   ```
   ./.cache/llama.cpp/build-rpc/bin/ggml-rpc-server -p 50052
   ```
4. Keep the Codespace running.

The RPC server is intentionally not made public. Use the GitHub CLI port bridge from Termux so the remote port becomes local-only on the phone.

## Termux main host
1. Build the local llama.cpp main host with `-DGGML_RPC=ON`.
2. Forward the Codespace RPC port into Termux:
   ```
   gh codespace ports forward 50052:50052 -c YOUR_CODESPACE_NAME
   ```
3. Run llama-server with:
   ```
   ./build-rpc/bin/llama-server -hf Qwen/Qwen2.5-Coder-3B-Instruct-GGUF:Q4_K_M -c 4096 --host 127.0.0.1 --port 8080 --rpc 127.0.0.1:50052
   ```

The Minecraft mod continues talking to `http://127.0.0.1:8080/v1`; the RPC layer is transparent to the mod.

## Important
llama.cpp currently describes its RPC backend as proof-of-concept and insecure on open networks. Do not expose port 50052 publicly; the GitHub CLI bridge keeps it private.
