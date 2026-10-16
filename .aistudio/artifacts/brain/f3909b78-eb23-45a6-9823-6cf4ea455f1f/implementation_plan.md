# Implementation Plan - SAF Storage Mapping Layer

Implement a robust mapping layer that correctly links the Storage Access Framework (SAF) tree URI chosen by the user to all session file operations (directory creation, photo importing, syncing, serving over HTTP, and downloading), ensuring the app reads/writes data from the user-selected SAF folder instead of the default app directory, and enforcing SAF selection when not set.

## User Requirements
- When SAF is set, every new session folder and photo must be stored inside the user-selected SAF directory tree.
- Enforce SAF folder selection before creating sessions or importing/saving photos if no SAF URI is configured.

## Proposed Changes

### 1. `FotoSyncViewModel.kt`
- Add a helper `getSafSessionDirectory(context: Context, session: PhotoSessionEntity): DocumentFile?` to resolve the session folder inside the SAF tree (`customStorageUri` -> `FotoSync_Studio` -> `session.folderName`).
- Update `createSession` to check if `customStorageUri` is set; if not, require/prompt user or fallback safely.
- Update `importPhotosFromUris` and `syncSessionFolder` to work seamlessly with SAF `DocumentFile` URIs when `customStorageUri` is active.
- Update Ktor server's `photoBytesProvider` and file providers to load bytes via `ContentResolver` / `DocumentFile` when SAF is used.

### 2. UI Validation & Prompt (`FotoSyncApp.kt`)
- When user taps "Tambah Sesi" or tries to import photos without a configured SAF folder, prompt them with a Toast/Dialog requiring them to set the SAF storage path first.

## Verification Plan
- Compile applet with `compile_applet`.
- Run tests.
