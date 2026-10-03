package com.voiceflowkeyboard.ime;

/** Immutable editor/provider snapshot; stopping a recorder cannot change its decoder. */
final class CaptureSession {
    final long generation;
    final String packageName;
    final int fieldId;
    final int inputType;
    final int selectionStart;
    final int selectionEnd;
    final String provider;
    final boolean retainHistory;

    CaptureSession(long generation, String packageName, int fieldId, int inputType,
                   String provider, boolean retainHistory, int selectionStart, int selectionEnd) {
        this.generation = generation;
        this.packageName = packageName;
        this.fieldId = fieldId;
        this.inputType = inputType;
        this.selectionStart = selectionStart;
        this.selectionEnd = selectionEnd;
        this.provider = provider;
        this.retainHistory = retainHistory;
    }

    boolean sameEditor(long currentGeneration, String app, int field, int type) {
        return generation == currentGeneration && packageName != null && packageName.equals(app)
                && fieldId == field && inputType == type && EditorPolicy.allowsVoice(app, type);
    }
    boolean matches(long currentGeneration, String app, int field, int type, int start, int end) {
        return sameEditor(currentGeneration, app, field, type) && matchesSelection(start, end);
    }

    boolean matchesSelection(int start, int end) {
        return selectionStart >= 0 && selectionEnd >= 0
                && selectionStart == start && selectionEnd == end;
    }
}
