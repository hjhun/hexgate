package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/** SSH stream adapter. All emulator access is serialized on the Android UI thread. */
public final class TerminalSession extends TerminalOutput {
    public interface Transport {
        void send(byte[] data);
        void resize(int columns, int rows);
    }
    private final Transport transport;
    private final TerminalSessionClient client;
    private TerminalEmulator emulator;
    private final java.io.ByteArrayOutputStream inputBatch = new java.io.ByteArrayOutputStream();
    private int inputBatchDepth;

    public TerminalSession(Transport transport, TerminalSessionClient client) {
        this.transport = transport;
        this.client = client;
        emulator = new TerminalEmulator(this, 80, 24, 9, 18, 2000, client);
    }
    public TerminalEmulator getEmulator() { return emulator; }
    public void updateSize(int columns, int rows, int width, int height) {
        emulator.resize(columns, rows, width, height);
        transport.resize(columns, rows);
    }
    public void receive(byte[] bytes) {
        emulator.append(bytes, bytes.length);
        client.onTextChanged(this);
    }
    @Override public void write(byte[] bytes, int offset, int count) {
        byte[] copy = java.util.Arrays.copyOfRange(bytes, offset, offset + count);
        if (inputBatchDepth > 0) {
            if (inputBatch.size() + count > 16384) flushInputBatch();
            inputBatch.write(copy, 0, copy.length);
        } else transport.send(copy);
    }
    public void beginInputBatch() { inputBatchDepth++; }
    public void endInputBatch() { if (--inputBatchDepth == 0) flushInputBatch(); }
    private void flushInputBatch() {
        if (inputBatch.size() == 0) return;
        byte[] bytes = inputBatch.toByteArray();
        inputBatch.reset();
        transport.send(bytes);
    }
    public void writeCodePoint(boolean alt, int codePoint) {
        write((alt ? "\033" : "") + new String(Character.toChars(codePoint)));
    }
    @Override public void titleChanged(String oldTitle, String newTitle) { client.onTitleChanged(this); }
    @Override public void onCopyTextToClipboard(String text) { client.onCopyTextToClipboard(this, text); }
    @Override public void onPasteTextFromClipboard() { client.onPasteTextFromClipboard(this); }
    @Override public void onBell() { client.onBell(this); }
    @Override public void onColorsChanged() { client.onColorsChanged(this); }
}
