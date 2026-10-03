#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
禅息 (ZenXi) - 提示音合成脚本

用加法合成生成铜铃 / 水晶 / 颂钵 / 木鱼 / 水滴 等音色，
输出 44.1kHz / 16bit / 单声道 PCM WAV 到 app/res/raw/。

所有音频均为程序生成，无第三方版权素材。
"""

import math
import os
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.normpath(os.path.join(HERE, "..", "app", "res", "raw"))


# ---------------------------------------------------------------- utilities

def _noise_burst(n, dur=0.006, seed=0):
    """短促的高频敲击噪声，用来模拟槌子敲到金属/木头的瞬间。"""
    rng = np.random.default_rng(seed)
    k = min(n, max(8, int(SR * dur)))
    x = rng.normal(0.0, 1.0, k)
    y = np.zeros(k)
    a = 0.90
    px = 0.0
    py = 0.0
    for i in range(k):
        py = a * (py + x[i] - px)
        px = x[i]
        y[i] = py
    y *= np.exp(-np.arange(k) / (SR * dur * 0.35))
    out = np.zeros(n)
    out[:k] = y
    return out


def bell(f0, dur, partials, attack=0.004, detune=3.0, strike=0.18, seed=7):
    """
    钟/铃类音色：f0 基频，partials = [(频率比, 振幅, 衰减时间常数)]。
    每个分音用两个失谐振荡器叠加，产生真实的金属"拍频"光泽。
    """
    n = int(SR * dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    rng = np.random.default_rng(seed)
    for ratio, amp, tau in partials:
        f = f0 * ratio
        phase = rng.uniform(0.0, 2.0 * math.pi)
        d = 2.0 ** (detune / 1200.0)
        osc = 0.62 * np.sin(2.0 * math.pi * f * t + phase)
        osc += 0.38 * np.sin(2.0 * math.pi * f * d * t + phase * 0.7)
        amp_env = amp * np.exp(-t / tau) * np.clip(t / attack, 0.0, 1.0)
        out += amp_env * osc
    if strike > 0.0:
        out += strike * _noise_burst(n, seed=seed + 1)
    return out


def water_drop(f_hi, f_lo, dur, tau=0.20, tau_f=0.09, amp=1.0, seed=11):
    """水滴音：频率从 f_hi 指数滑落到 f_lo。"""
    n = int(SR * dur)
    t = np.arange(n) / SR
    glide = (f_hi - f_lo) * tau_f * (1.0 - np.exp(-t / tau_f))
    phase = 2.0 * math.pi * (f_lo * t + glide)
    env = np.exp(-t / tau) * np.clip(t / 0.006, 0.0, 1.0)
    body = np.sin(phase) + 0.18 * np.sin(2.0 * phase + 0.4)
    body += 0.25 * _noise_burst(n, dur=0.004, seed=seed) * np.exp(-t / 0.05)
    return amp * env * body


def swell(f0, dur, attack=0.45, release=0.55, amp=1.0):
    """呼吸提示：极缓的进气/出气音团。"""
    n = int(SR * dur)
    t = np.arange(n) / SR
    a = np.clip(t / attack, 0.0, 1.0) * np.clip((dur - t) / release, 0.0, 1.0)
    a = a ** 1.6
    tone = np.sin(2.0 * math.pi * f0 * t) + 0.30 * np.sin(2.0 * math.pi * f0 * 1.5 * t + 1.1)
    tone += 0.12 * np.sin(2.0 * math.pi * f0 * 2.02 * t + 2.0)
    return amp * a * tone


def put(dst, src, at_sec):
    """把一段波形叠加到 dst 的指定时刻。"""
    i = int(SR * at_sec)
    if i >= len(dst):
        return
    k = min(len(src), len(dst) - i)
    dst[i:i + k] += src[:k]


def glide_cue(f_from, f_to, dur, amp=1.0, tau=0.16):
    """上滑音：连续的 440→880Hz 滑上去，听起来像「ou～」。"""
    n = int(SR * dur)
    t = np.arange(n) / SR
    glide = (f_to - f_from) * tau * (1.0 - np.exp(-t / tau))
    phase = 2.0 * math.pi * (f_from * t + glide)
    a = np.clip(t / 0.05, 0.0, 1.0) * np.exp(-t / (dur * 0.45))
    return amp * a * (np.sin(phase) + 0.22 * np.sin(2.0 * phase + 0.7))


def start_double():
    """起势音之一：两声上行的清铃（E5 → B5），不做滑音，垫一层低频暖底。"""
    first = bell(659.25, 1.30,
                 [(1.0, 1.00, 0.60), (2.00, 0.30, 0.30), (3.01, 0.12, 0.15),
                  (4.60, 0.05, 0.08)],
                 attack=0.008, detune=2.4, strike=0.10, seed=61)
    second = bell(987.77, 1.45,
                  [(1.0, 0.85, 0.72), (2.00, 0.26, 0.34), (3.02, 0.10, 0.16)],
                  attack=0.008, detune=2.0, strike=0.08, seed=67)
    warmth = bell(392.00, 1.90,
                  [(1.0, 0.34, 1.10), (2.00, 0.12, 0.60)],
                  attack=0.014, detune=1.5, strike=0.0, seed=71)
    out = np.zeros(int(SR * 2.0))
    put(out, warmth, 0.0)
    put(out, first, 0.0)
    put(out, second, 0.26)
    return out


def start_single():
    """起势音之一：单独一声清铃。"""
    return bell(880.00, 1.55,
                [(1.0, 1.00, 0.78), (2.00, 0.30, 0.36), (3.01, 0.10, 0.16),
                 (4.60, 0.04, 0.08)],
                attack=0.008, detune=2.2, strike=0.10, seed=83)


def start_wood():
    """起势音之一：木鱼双叩。"""
    tap = bell(880.00, 0.26,
               [(1.0, 1.00, 0.055), (2.40, 0.50, 0.028), (3.80, 0.25, 0.016)],
               attack=0.0015, detune=5.0, strike=0.50, seed=89)
    out = np.zeros(int(SR * 0.85))
    put(out, tap, 0.0)
    put(out, tap * 0.82, 0.22)
    return out


def start_water():
    """起势音之一：水滴。"""
    return water_drop(1600.0, 520.0, 0.90, tau=0.20, tau_f=0.10)


def finish(sig, peak=0.86, fade_ms=45):
    """归一化 + 尾部淡出，避免爆音。"""
    sig = np.asarray(sig, dtype=np.float64)
    m = float(np.max(np.abs(sig))) if sig.size else 0.0
    if m > 0:
        sig = sig / m * peak
    k = max(8, int(SR * fade_ms / 1000.0))
    if sig.size > k:
        ramp = 0.5 * (1.0 + np.cos(np.linspace(0.0, math.pi, k)))
        sig[-k:] *= ramp
    return sig


def write_wav(name, sig):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name + ".wav")
    pcm = np.clip(sig, -1.0, 1.0)
    data = (pcm * 32767.0).astype("<i2").tobytes()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)
    return path, len(data) + 44


# ---------------------------------------------------------------- 音色定义

VOICE_DEFS = {
    # 铜铃：传统手铃，明亮清脆
    "brass": (
        dict(f0=1318.51, dur=1.45, strike=0.16, detune=3.2,
             partials=[(1.0, 1.00, 0.55), (2.00, 0.42, 0.34), (2.76, 0.30, 0.22),
                       (4.16, 0.14, 0.13), (5.43, 0.09, 0.09), (8.90, 0.05, 0.05)]),
        dict(f0=440.00, dur=2.85, strike=0.20, detune=4.0,
             partials=[(1.0, 1.00, 1.30), (2.00, 0.50, 0.85), (2.92, 0.34, 0.50),
                       (4.18, 0.16, 0.30), (6.10, 0.08, 0.18)]),
    ),
    # 水晶：清透、尾音短，适合不打扰的场合
    "crystal": (
        dict(f0=2093.00, dur=1.15, strike=0.05, detune=1.6,
             partials=[(1.0, 1.00, 0.62), (2.00, 0.32, 0.34), (3.01, 0.12, 0.16),
                       (5.40, 0.05, 0.08)]),
        dict(f0=1046.50, dur=2.20, strike=0.05, detune=1.6,
             partials=[(1.0, 1.00, 1.15), (2.00, 0.38, 0.60), (3.00, 0.15, 0.30),
                       (4.50, 0.06, 0.15)]),
    ),
    # 颂钵：低频悠长、共鸣饱满
    "bowl": (
        dict(f0=587.33, dur=2.10, strike=0.11, detune=2.0,
             partials=[(1.0, 1.00, 1.40), (2.00, 0.55, 1.10), (2.98, 0.30, 0.80),
                       (4.20, 0.12, 0.40), (5.60, 0.06, 0.25)]),
        dict(f0=293.66, dur=3.60, strike=0.13, detune=1.8,
             partials=[(1.0, 1.00, 2.20), (2.00, 0.50, 1.60), (2.94, 0.30, 1.10),
                       (4.10, 0.14, 0.60)]),
    ),
    # 木鱼：干脆短促，适合极简练习
    "wood": (
        dict(f0=1046.50, dur=0.30, strike=0.52, detune=6.0, attack=0.0015,
             partials=[(1.0, 1.00, 0.060), (2.20, 0.50, 0.030), (3.60, 0.30, 0.020)]),
        dict(f0=622.25, dur=0.46, strike=0.46, detune=5.0, attack=0.0015,
             partials=[(1.0, 1.00, 0.095), (2.50, 0.50, 0.040), (4.00, 0.25, 0.020)]),
    ),
}


def build():
    total = 0
    made = []

    for voice, (ding_def, dang_def) in VOICE_DEFS.items():
        ding = bell(ding_def["f0"], ding_def["dur"], ding_def["partials"],
                    attack=ding_def.get("attack", 0.004), detune=ding_def["detune"],
                    strike=ding_def["strike"], seed=7)
        dang = bell(dang_def["f0"], dang_def["dur"], dang_def["partials"],
                    attack=dang_def.get("attack", 0.004), detune=dang_def["detune"],
                    strike=dang_def["strike"], seed=23)
        for suffix, sig in (("ding", ding), ("dang", dang)):
            path, size = write_wav("%s_%s" % (voice, suffix), finish(sig))
            made.append((os.path.basename(path), size))
            total += size

    # 水滴（第五种音色）
    for suffix, sig in (("ding", water_drop(1760.0, 560.0, 0.78, tau=0.19)),
                        ("dang", water_drop(900.0, 300.0, 1.25, tau=0.34, tau_f=0.13))):
        path, size = write_wav("water_%s" % suffix, finish(sig))
        made.append((os.path.basename(path), size))
        total += size

    # 准备倒计时的一声轻点（不区分音色）
    tick = bell(1760.0, 0.20, [(1.0, 1.0, 0.035), (2.40, 0.25, 0.020)],
                attack=0.0015, detune=2.0, strike=0.55, seed=31)
    path, size = write_wav("tick", finish(tick, peak=0.70))
    made.append((os.path.basename(path), size))
    total += size

    # 起势音：五种可选（设置里可切换）
    for name, sig, peak in (
            ("start", start_double(), 0.74),
            ("start_single", start_single(), 0.72),
            ("start_glide", glide_cue(440.0, 880.0, 0.70), 0.70),
            ("start_wood", start_wood(), 0.74),
            ("start_water", start_water(), 0.76)):
        path, size = write_wav(name, finish(sig, peak=peak))
        made.append((os.path.basename(path), size))
        total += size

    # 收束长铃
    settle = bell(261.63, 4.20, [(1.0, 1.00, 2.60), (2.00, 0.50, 1.80), (3.00, 0.28, 1.20),
                                 (4.16, 0.12, 0.70), (5.90, 0.06, 0.40)],
                  attack=0.006, detune=1.5, strike=0.09, seed=41)
    path, size = write_wav("settle", finish(settle, peak=0.82))
    made.append((os.path.basename(path), size))
    total += size

    # 半程轻铃
    half = bell(1046.50, 1.05, [(1.0, 1.00, 0.52), (2.00, 0.28, 0.24), (3.02, 0.08, 0.12)],
                attack=0.006, detune=2.0, strike=0.07, seed=53)
    path, size = write_wav("half", finish(half, peak=0.55))
    made.append((os.path.basename(path), size))
    total += size

    # 呼吸引导：吸气偏高、呼气偏低
    path, size = write_wav("breath_in", finish(swell(392.00, 1.60, amp=0.95), peak=0.34))
    made.append((os.path.basename(path), size))
    total += size
    path, size = write_wav("breath_out", finish(swell(293.66, 1.90, amp=0.95), peak=0.30))
    made.append((os.path.basename(path), size))
    total += size

    for name, size in made:
        print("  %-20s %7.1f KB" % (name, size / 1024.0))
    print("共 %d 个音频，合计 %.2f MB -> %s" % (len(made), total / 1048576.0, OUT_DIR))


if __name__ == "__main__":
    build()
