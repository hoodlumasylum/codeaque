# Lock a phone to CodeAque (device owner kiosk)

This is for a parent who wants Kids mode to really stay on the CodeAque home: no Recents swipe, no jumping to other apps, no installing new apps from the phone. It is optional. CodeAque still works without it (Kids mode, the parent PIN, screen time, and the softer "Lock phone to CodeAque in Kids mode" pin).

**This needs a factory-reset phone with no Google account added yet.** Android will refuse the setup if any account is already on the device. Back up what you need, then reset the phone and do these steps *before* signing in to Google.

These steps were written for the Dell PC running Linux Mint. They have not been tried end-to-end on a real phone in this release.

## If the phone is rooted (for example the K56)

Some handhelds already have `su` (on the K56 it is at `/system/xbin/su`). Root is **optional** — CodeAque never requires it for ordinary use.

1. Install CodeAque on the reset phone. Do not add a Google account yet.
2. Open CodeAque, go to Settings, and choose **Lock phone to CodeAque**. It asks for the parent PIN.
3. Allow the superuser / Magisk prompt if it appears.
4. If it works, you will see a short success message. Turn on Kids mode. The phone should stay on CodeAque.
5. If it fails because an account is already on the phone, Android Settings > Accounts, remove every account (or factory-reset again), and retry. If there is no `su`, follow the adb steps below (the same screen can show them).

To undo: Settings (parent PIN) > **Remove device owner**. Do this before you sell or give the phone away, or the next person may be stuck.

## From the Dell (Linux Mint) with adb

1. On the phone: Settings > About phone, tap **Build number** seven times until it says you are a developer. Go back, open **Developer options**, and turn on **USB debugging**.
2. On the Dell, open a terminal and install adb if it is not already there:

       sudo apt install adb

3. Plug the phone into the Dell with a **data** USB cable (a charge-only cable will not work). If the phone asks "Allow USB debugging?", tick Always allow and tap Allow.
4. In the same terminal, check that the computer can see the phone:

       adb devices

   You should see a serial number and the word `device`. If it says `unauthorized`, accept the prompt on the phone and run `adb devices` again.
5. Set CodeAque as the device owner (only works when no accounts are on the phone):

       adb shell dpm set-device-owner com.codeaque.launcher/.AdminReceiver

   A success line means it worked. If the command talks about accounts or "not allowed", there is still an account on the phone — remove it or factory-reset, then try again. (An older command used `.CodeAqueAdmin` instead of `.AdminReceiver`; that still works if you already used it.)
6. Open CodeAque and turn on Kids mode. The full kiosk lock starts: Recents / Home swipe is blocked, notifications stay down, and installing apps is disallowed until a parent leaves Kids mode with the PIN.

## What a parent can and cannot do after this

- Leaving Kids mode still needs the parent PIN. That also turns off the install and factory-reset restrictions.
- **Remove device owner** (Settings, parent PIN) gives the phone back to a normal state.
- Without device owner, CodeAque can only use ordinary screen pinning and a "bring me back" fallback. A child can usually unpin with Back + Recents, and Android 10+ may block the bounce unless "Display over other apps" and Usage access are granted. The status bar, power menu and a safe-mode boot cannot be fully blocked without device owner.

## What was tested

The settings, PIN gates, `dpm` command string, and the admin receiver are checked in unit / source tests on a PC. Setting device owner with `su` or adb, and the kiosk lock on a real Android 14 K56 or any real phone, has **not** been tried in this release.
