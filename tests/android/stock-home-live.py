#!/usr/bin/env python3
"""Opt-in Xiaomi UI check: opens the real stock home, then returns with Home. No role/package shell writes."""
import os
import subprocess
import time

adb = [os.environ.get('ADB', 'adb'), '-s', os.environ['ANDROID_SERIAL'], 'shell']
free = 'io.freemytv/.HomeActivity'
stock = 'com.google.android.tvlauncher/.MainActivity'


def shell(*args):
    return subprocess.check_output(adb + list(args), text=True, timeout=20)


def foreground(component, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        top = next((line for line in shell('dumpsys', 'activity', 'activities').splitlines()
                    if 'topResumedActivity=' in line), '')
        if component in top:
            return
        time.sleep(0.3)
    raise AssertionError('Expected foreground ' + component + ', got: ' + top.strip())


assert shell('cmd', 'role', 'get-role-holders', 'android.app.role.HOME').strip() == 'io.freemytv', \
    'Choose Free My TV as default on the TV before this test'
for visit in range(1):
    # A HOME intent also resets focus to the continuation row; the test activates the real visible shortcut.
    shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME', '-n', free)
    foreground(free)
    time.sleep(1)
    for key in ('KEYCODE_DPAD_UP', 'KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_CENTER'):
        shell('input', 'keyevent', key)
    foreground(stock)
    assert shell('cmd', 'role', 'get-role-holders', 'android.app.role.HOME').strip() == 'io.freemytv', \
        'Stock visit changed the HOME role'
    # Injected adb keys bypass accessibility filtering; use the physical remote for this acceptance check.
    if os.environ.get('RETURN_MODE') == 'activity':
        shell('am', 'start', '-W', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME', '-n', free)
    else:
        print('READY: press physical Home on the TV remote', flush=True)
    foreground(free, 180)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        resolved = shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN',
                         '-c', 'android.intent.category.HOME')
        enabled = shell('settings', 'get', 'secure', 'enabled_accessibility_services')
        if free in resolved and 'io.freemytv.RemoteKeyService' not in enabled:
            break
        time.sleep(0.5)
    assert free in resolved, 'Temporary stock visit did not restore native HOME resolution: ' + resolved
    enabled = shell('settings', 'get', 'secure', 'enabled_accessibility_services')
    assert 'io.freemytv.RemoteKeyService' not in enabled, 'Temporary Xiaomi Home interception remained enabled'
    shell('input', 'keyevent', 'KEYCODE_HOME')
    foreground(free)
    print('PASS: ' + os.environ.get('RETURN_MODE', 'physical Home') + ' return, native HOME resolution and temporary service cleanup', flush=True)
