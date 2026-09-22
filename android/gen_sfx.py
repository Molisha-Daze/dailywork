#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
「未竟」UI 音效合成器 —— 纯标准库实现，无第三方依赖（无 numpy、无 ffmpeg）。

为什么自己合成而不是下载素材：
1. 版权干净 —— 全部由本脚本数值生成，我方原创，可直接声明 CC0，发布时零溯源负担；
2. 体积可控 —— 每个音效 5~80KB，总量约 350KB，对 21MB 的 release 可忽略；
3. 可调参 —— 觉得某个音太尖/太长，改一个数字重跑即可，不用求着美术给资源；
4. 离线可行 —— 本机没有 ffmpeg、没有 numpy，但 `wave` + `math` 是标准库，够用。

输出：44.1kHz / 16bit / 单声道 PCM WAV（SoundPool 原生支持的格式）。
默认写入 android/sfx-preview/，试听满意后再决定是否搬进 app/src/main/res/raw/。

用法：
    python gen_sfx.py                # 生成到默认目录 sfx-preview/
    python gen_sfx.py res/raw        # 指定输出目录（定稿后搬进资源目录）

设计原则（与 Haptics 对齐）：
- 响度即层级：越重要的成就，峰值越高、尾音越长；细节音要明显退让。
- 每个音效首尾必须淡入淡出，否则波形从 0 突跳会爆出 "咔" 声（真实事故来源）。
- 一组音效之间音高体系一致（全部落在 C 大调音阶），听起来才像同一个产品。
"""

import array
import math
import os
import random
import sys
import wave

SAMPLE_RATE = 44100

# C 大调音高表（Hz）。刻意只取自然音，避免出现游离的半音让整组音效不和谐。
C5, D5, E5, G5 = 523.25, 587.33, 659.25, 783.99
C6, E6, G6, A6 = 1046.50, 1318.51, 1567.98, 1760.00
C7 = 2093.00

# 首尾渐变时长：2ms 淡入防起音爆音，4ms 淡出防截断爆音。
FADE_IN_MS = 2.0
FADE_OUT_MS = 4.0


# ---------------------------------------------------------------- 基础工具

def n_samples(duration_ms):
    return int(SAMPLE_RATE * duration_ms / 1000.0)


def trim_tail(buf, floor_db=-58.0):
    """从尾部裁掉已经衰减到听不见的空白。

    铃铛类音效的指数衰减尾巴很长，但 -58dB 以下人耳在手机扬声器上根本听不出。
    裁掉能省 10~20% 体积，而且让 SoundPool 的流更早释放。
    """
    threshold = 10 ** (floor_db / 20.0)
    end = len(buf)
    while end > 1 and abs(buf[end - 1]) < threshold:
        end -= 1
    return buf[:min(len(buf), end + n_samples(4))]


def apply_fades(buf):
    """给首尾套上斜坡。**必须做**：波形从 0 直接跳到非零值就是一次宽带脉冲，
    听起来就是一声 "咔"，跟音效本身无关，纯属工程噪声。"""
    n = len(buf)
    fade_in = min(n_samples(FADE_IN_MS), n // 2)
    fade_out = min(n_samples(FADE_OUT_MS), n // 2)
    for i in range(fade_in):
        buf[i] *= i / fade_in
    for i in range(fade_out):
        buf[n - 1 - i] *= i / fade_out
    return buf


def normalize(buf, peak):
    """归一化到指定峰值。peak 就是「响度层级」的载体 —— 越重要的音给越高。"""
    current = max((abs(v) for v in buf), default=0.0)
    if current <= 1e-9:
        return buf
    k = peak / current
    return [v * k for v in buf]


def add_at(base, layer, offset_ms, gain=1.0):
    """把 layer 叠加到 base 的指定时间偏移处（浮点缓冲区，允许溢出后统一归一化）。"""
    off = n_samples(offset_ms)
    need = off + len(layer)
    if need > len(base):
        base = base + [0.0] * (need - len(base))
    for i, v in enumerate(layer):
        base[off + i] += v * gain
    return base


# ------------------------------------------------------------ 合成算子

def bell(freq, duration_ms, tau_ms, fm_ratio=3.0, fm_index=1.3,
         fm_decay_ms=45.0, harmonic2=0.0, gain=1.0):
    """FM 铃铛 —— 本项目「成就类」音效的主力音色。

    原理：载波 sin(2πft) 的相位被一个高频调制波推来推去，产生非谐波的金属质感。
    调制指数（fm_index）随时间快速衰减，所以起音是"叮"、尾部回落到纯净正弦，
    这正是真实钟/铃的行为特征。

    比直接叠加整数泛音好在哪：整数泛音听着像电子琴，FM 的旁瓣是分数比的，
    耳朵会认定"这是个打击乐器"，而不是"这是个提示音"。
    """
    n = n_samples(duration_ms)
    tau = tau_ms / 1000.0
    fm_tau = fm_decay_ms / 1000.0
    out = [0.0] * n
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t / tau)
        mod = fm_index * math.exp(-t / fm_tau) * math.sin(2 * math.pi * fm_ratio * freq * t)
        v = math.sin(2 * math.pi * freq * t + mod)
        if harmonic2:
            # 少量二次谐波：给音色加一点"厚度"，太多会变刺耳。
            v += harmonic2 * math.exp(-t / (tau * 0.6)) * math.sin(4 * math.pi * freq * t)
        out[i] = gain * env * v
    return out


def wood_click(freq, duration_ms, tau_ms, gain=1.0, body=0.35, seed=20260922):
    """木质轻点 —— 给「细节类」音效用。

    做法：白噪声 乘以 一个正弦（环形调制），等效于以 freq 为中心的窄带噪声。
    纯噪声只有"沙"，纯正弦只有"哔"，两者相乘才是"嗒"。
    """
    n = n_samples(duration_ms)
    rnd = random.Random(seed)
    tau = tau_ms / 1000.0
    out = [0.0] * n
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t / tau)
        noise = rnd.uniform(-1.0, 1.0)
        out[i] = gain * env * (noise * math.sin(2 * math.pi * freq * t) + body * math.sin(2 * math.pi * freq * t))
    return out


def glide(f0, f1, duration_ms, tau_ms, gain=1.0, harmonic2=0.25):
    """下滑音 —— 「清零 / 删除」这类"事情已经过去了"的确认音。

    刻意用下行而不是上行：上行听起来像"又完成了一件事"，会误导；
    下行收束感强，配合低沉音区，传递的是「已归档」而不是「被奖励」。

    频率按指数插值（而不是线性）：音乐上的滑音听觉是等比的，
    线性滑音会让人觉得前半段几乎没动、后半段突然掉下去。
    """
    n = n_samples(duration_ms)
    tau = tau_ms / 1000.0
    out = [0.0] * n
    phase = 0.0
    for i in range(n):
        t = i / SAMPLE_RATE
        prog = i / max(1, n - 1)
        f = f0 * ((f1 / f0) ** prog)
        # 相位累加而非直接算 sin(2πft)：f 在变时，直接代入会算错相位、产生跳变杂音。
        phase += 2 * math.pi * f / SAMPLE_RATE
        env = math.exp(-t / tau)
        v = math.sin(phase)
        if harmonic2:
            v += harmonic2 * math.sin(2 * phase)
        out[i] = gain * env * v
    return out


def pad(freq, duration_ms, attack_ms, tau_ms, gain=0.3):
    """柔和的底层铺底 —— 只用来给「大成就」音效加分量，单独听几乎察觉不到。"""
    n = n_samples(duration_ms)
    tau = tau_ms / 1000.0
    attack = attack_ms / 1000.0
    out = [0.0] * n
    for i in range(n):
        t = i / SAMPLE_RATE
        env = (t / attack) if t < attack else math.exp(-(t - attack) / tau)
        out[i] = gain * env * math.sin(2 * math.pi * freq * t)
    return out


# ---------------------------------------------------------------- 音效清单
# 每个条目：文件名 -> (生成函数, 峰值目标 dBFS 的反线性值)
# 峰值目标刻意拉开层级：成就音 0.82~0.90，确认音 0.60，细节音 0.42。
# 全部留足 headroom，绝不归一化到 1.0 —— 峰值顶到满刻度的音效在手机扬声器上会削波失真。

def build_achieve_habit():
    """习惯打卡完成 —— 全 App 出现频率最高的正反馈。

    双音上行（C6→E6）而不是单音：单音只是"哔"，两个音才构成一个"动机"，
    耳朵会把它当成一小句音乐，愉悦感显著强于提示音。
    90ms 的间隔来自无数铃声的经验值：再快会混成一片，再慢就不成句。
    """
    buf = [0.0] * n_samples(460)
    buf = add_at(buf, bell(C6, 460, tau_ms=105, gain=1.0), 0)
    buf = add_at(buf, bell(E6, 370, tau_ms=100, gain=0.92), 90)
    return trim_tail(buf)


def build_achieve_plan():
    """一个计划的子任务全部勾满 —— 比单习惯更重，但还不是全天达成。

    三音琶音（C6→E6→G6），尾音更长。层级清晰：2 音 = 单个，3 音 = 一组，
    用户听两次就能建立"音数越多、成就越大"的直觉，不需要看屏幕。
    """
    buf = [0.0] * n_samples(720)
    buf = add_at(buf, bell(C6, 720, tau_ms=150, gain=0.95), 0)
    buf = add_at(buf, bell(E6, 650, tau_ms=150, gain=0.90), 80)
    buf = add_at(buf, bell(G6, 580, tau_ms=165, gain=0.95), 160)
    return trim_tail(buf)


def build_achieve_day():
    """今日排期全部完成 —— 全 App 最高等级的时刻，配一个完整的收尾。

    四音上行 + 低八度铺底 + 最后一音延长并加二次谐波做"绽放"。
    这是唯一允许超过 0.8 秒的音效；稀有性正是它值钱的原因。
    """
    buf = [0.0] * n_samples(1050)
    buf = add_at(buf, pad(C5 / 2, 1050, attack_ms=8, tau_ms=420, gain=0.30), 0)
    buf = add_at(buf, bell(C6, 900, tau_ms=150, gain=0.85), 0)
    buf = add_at(buf, bell(E6, 820, tau_ms=150, gain=0.85), 78)
    buf = add_at(buf, bell(G6, 740, tau_ms=160, gain=0.88), 156)
    buf = add_at(buf, bell(C7, 700, tau_ms=230, fm_index=1.6, harmonic2=0.22, gain=1.0), 234)
    return trim_tail(buf)


def build_counter_goal():
    """计数器刚好达标 —— 单音"叮"，但要比普通步进亮。

    用 A6：明显高于成就音的 C6~G6 区间，听感上是"跳出来"的，
    符合"刚刚好踩线"这种轻快的小惊喜。刻意比 achieve_habit 短，不抢戏。
    """
    buf = [0.0] * n_samples(360)
    buf = add_at(buf, bell(A6, 360, tau_ms=100, fm_ratio=3.5, gain=1.0), 0)
    return trim_tail(buf)


def build_counter_limit():
    """独立计数器到达上限 —— 双音叠置的和弦"铛"，表示"满了、到头了"。

    用 C5+G5 的五度而不是单音：五度是唯一"既稳定又未解决"的音程，
    听起来像终点但不沮丧。音区低一档，和 counter_goal 明确区分开：
    达标是过程里的亮点，到达上限是这件事彻底结束。
    """
    buf = [0.0] * n_samples(520)
    buf = add_at(buf, bell(C5, 520, tau_ms=170, fm_index=1.1, gain=0.85), 0)
    buf = add_at(buf, bell(G5, 470, tau_ms=165, fm_index=1.1, gain=0.75), 12)
    return trim_tail(buf)


def build_clear():
    """计数器清零 / 删除确认 —— 破坏性操作已执行。

    无音高感的下滑低音，音量明显压到成就音之下。
    不要给破坏性操作配好听的音效：那等于在鼓励用户多删。
    这一声的职责只是"收到并执行了"，不是"干得好"。
    """
    buf = [0.0] * n_samples(280)
    buf = add_at(buf, glide(310.0, 150.0, 280, tau_ms=95, gain=1.0, harmonic2=0.22), 0)
    return trim_tail(buf)


def build_restore():
    """备份恢复完成 —— 低频起步的宽音程上行，传递"回来了"的踏实感。

    用 C5→G5（五度）而不是 C6→E6（三度）：音区低、音程宽，
    听感是"沉的、稳的"，和成就音效的"亮的、跳的"区分开。
    """
    buf = [0.0] * n_samples(620)
    buf = add_at(buf, bell(C5, 620, tau_ms=190, fm_index=1.2, gain=0.85), 0)
    buf = add_at(buf, bell(G5, 520, tau_ms=200, fm_index=1.2, harmonic2=0.15, gain=0.80), 105)
    return trim_tail(buf)


def build_undo():
    """取消打卡 / 撤销 —— 下行两音，默认关闭。

    为什么默认关：撤销是"退回"，给动作配声音会变成一种微妙的奖励，
    和"取消不该有正反馈"的产品逻辑冲突。留给需要听觉确认的用户自己开。
    """
    buf = [0.0] * n_samples(330)
    buf = add_at(buf, bell(G5, 330, tau_ms=85, gain=0.9), 0)
    buf = add_at(buf, bell(D5, 280, tau_ms=80, gain=0.85), 82)
    return trim_tail(buf)


def build_step_soft():
    """轻步进 —— 子任务勾选、计数器 ±1、表单保存成功，默认关闭。

    只有 60ms 的木质轻点。为什么默认关：计数器连点十下就是十声"嗒"，
    短时间内重复的短音会被大脑判定为噪音而不是反馈，这是听觉的基本机制，
    不是调音量能解决的。要做成默认开启必须配合很激进的节流，得不偿失。
    """
    buf = [0.0] * n_samples(70)
    buf = add_at(buf, wood_click(1750, 70, tau_ms=17, gain=1.0, body=0.30), 0)
    return trim_tail(buf)


SOUNDS = [
    # (文件名, 生成函数, 峰值, 默认是否开启, 中文说明)
    ("sfx_achieve_habit",  build_achieve_habit,  0.86, True,  "单次打卡完成"),
    ("sfx_achieve_plan",   build_achieve_plan,   0.86, True,  "计划内子任务全部完成"),
    ("sfx_achieve_day",    build_achieve_day,    0.90, True,  "今日排期全部完成"),
    ("sfx_counter_goal",   build_counter_goal,   0.80, True,  "计数器达到目标"),
    ("sfx_counter_limit",  build_counter_limit,  0.78, True,  "独立计数器到达上限"),
    ("sfx_clear",          build_clear,          0.58, True,  "清零 / 删除确认"),
    ("sfx_restore",        build_restore,        0.80, True,  "备份恢复完成"),
    ("sfx_undo",           build_undo,           0.62, False, "取消打卡 / 撤销"),
    ("sfx_step_soft",      build_step_soft,      0.42, False, "勾选子任务 / 计数器步进"),
]


# ---------------------------------------------------------------- 输出

def write_wav(path, buf):
    data = array.array("h", (int(max(-1.0, min(1.0, v)) * 32767) for v in buf))
    if sys.byteorder == "big":          # WAV 固定小端；大端机器上必须换序
        data.byteswap()
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(data.tobytes())


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "sfx-preview")
    os.makedirs(out_dir, exist_ok=True)

    print(f"输出目录: {out_dir}")
    print(f"采样率: {SAMPLE_RATE}Hz / 16bit / 单声道\n")
    print(f"{'文件':<24}{'时长':>8}{'体积':>10}  默认   说明")
    print("-" * 78)

    total = 0
    for name, fn, peak, enabled, desc in SOUNDS:
        buf = apply_fades(normalize(fn(), peak))
        path = os.path.join(out_dir, name + ".wav")
        write_wav(path, buf)
        size = os.path.getsize(path)
        total += size
        ms = len(buf) / SAMPLE_RATE * 1000
        print(f"{name + '.wav':<24}{ms:>7.0f}ms{size / 1024:>9.1f}K  "
              f"{'开' if enabled else '关':<6} {desc}")

    print("-" * 78)
    print(f"{'合计':<24}{'':>8}{total / 1024:>9.1f}K")


if __name__ == "__main__":
    main()
