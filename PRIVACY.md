# Tend — Privacy Policy

_Last updated: 5 August 2026_

Tend is a personal habit, planning and task app for Android. It has no accounts,
no servers, and no analytics. This policy describes every way data moves.

## What Tend stores, and where

Everything you enter — habits, check-ins, tasks, plan blocks and notes — is
stored **only on your device**, in Tend's private app storage. The developer
cannot see it and has no way to retrieve it.

## What Tend collects about you

Nothing. There is no telemetry, no analytics SDK, no advertising, no crash
reporting service, and no user identifier of any kind.

## Calendar access

If you grant calendar permission, Tend reads your events **read-only** so they
can appear on the Plan timeline and so Auto-plan can schedule around them.
Calendar data is read on the device, used to draw the screen, and never written,
stored, or transmitted anywhere. You can revoke the permission at any time in
Android settings; Tend keeps working without it.

## Notifications and alarms

Tend schedules local reminders on your device for habit times, task due times,
and an optional nightly check-in. These never leave the device.

## AI features ("Ask Tend" and Auto-plan)

AI features are **off until you supply your own API key**. Without a key, Tend
uses a built-in offline parser and nothing is transmitted.

If you add a key, then when you send a message or run Auto-plan, Tend sends to
the provider you selected:

- the text you typed, and
- a summary of your current habits, open tasks and today's plan, so the reply
  makes sense.

That request goes directly from your device to:

- **Google** (Gemini API), or
- **Anthropic** (Claude API),

depending on which provider you chose. It does not pass through any server
operated by the developer. Your data is then handled under that provider's own
privacy policy and the terms of your account with them. Remove the key in
Settings to stop all outbound requests.

## Your API key

Your API key is stored encrypted on your device, in
`EncryptedSharedPreferences`, protected by the Android keystore. It is never
transmitted anywhere except to the provider it belongs to, and it is **excluded
from Android's cloud backup and device-transfer** — so it never leaves the
device it was entered on. It is also never written into Tend's own backup files.

## Backups you create

Tend can write a backup file, on demand or on a schedule, into a folder you
choose with the Android file picker. The file contains your habits, check-ins,
tasks, plans, notes and app settings, in plain JSON — deliberately readable, so
your data isn't trapped in this app. It does **not** contain your API key.

Tend writes that file and nothing more: it does not upload backups anywhere. If
the folder you choose is itself synced by another app — Google Drive, Dropbox,
your device maker's cloud — that app's own policy governs the copy it makes.

Android's system backup may also copy Tend's app data to your Google account if
you have that enabled at the OS level. That is a Google feature covered by
Google's privacy policy, and you control it in your device settings.

## Sharing and exporting

The share and PDF-export features hand a file to whatever app you pick from the
Android share sheet. What happens next is up to that app.

## Children

Tend is not directed at children and collects no data from anyone.

## Deleting your data

Uninstalling Tend deletes all of its on-device data. Backup files you created
live in the folder you chose and must be deleted there — that's intentional, so
an uninstall can't erase your history.

## Changes

Material changes to this policy will be published here with an updated date.

## Contact

Questions about this policy: parthsh.ind@gmail.com
