import json, re

POS = ["志", "勤", "学", "行", "积", "恒", "步", "进", "千里", "光阴", "少年", "努力",
       "坚", "磨", "苦", "读书", "知", "思", "问", "山", "海", "路", "远", "高",
       "春", "晨", "朝", "新", "起", "成", "立", "明", "忍", "勇", "敢", "强",
       "寸", "日", "月", "岁", "时", "今", "始", "为", "作", "功", "名"]
NEG = ["愁", "泪", "恨", "怨", "悲", "妾", "郎", "相思", "肠断", "酒", "醉", "死",
       "孤", "寒", "空", "梦", "病", "老", "别", "离", "罗", "鬓", "钗", "莺", "燕",
       "鸳", "枕", "香", "啼", "瘦", "纱", "烛", "凄", "黯", "销", "残", "暮", "灰"]
BAD_SRC = ["一言官方", "群", "贴吧", "百度", "知乎", "段子", "网", "微博", "网易云"]


def score(text, who, src, kind):
    if kind == "k":
        s = 0
    else:
        s = 0
    for w in POS:
        if w in text:
            s += 2
    for w in NEG:
        if w in text:
            s -= 5
    if who:
        s += 3
    for w in BAD_SRC:
        if w in (src or ""):
            s -= 20
    # 太短的格言撑不起卡片，太长的一行放不下
    n = len(text)
    if n < 8:
        s -= 8
    elif n > 30:
        s -= 8
    if "\n" in text:
        s -= 10
    return s


out = []
for c, kind in [("i", "i"), ("k", "k")]:
    for x in json.load(open(f"{c}.json", encoding="utf-8")):
        t = x["hitokoto"].strip()
        s = score(t, x.get("from_who"), x.get("from"), kind)
        out.append((s, kind, t, x.get("from_who"), x.get("from")))

out.sort(key=lambda r: -r[0])
seen = set()
picked = []
for s, kind, t, who, src in out:
    if t in seen:
        continue
    seen.add(t)
    picked.append((s, kind, t, who, src))

print(f"总候选去重后 {len(picked)}")
for s, kind, t, who, src in picked[:110]:
    print(f"[{s:4d}][{kind}] {t} —— {who or '?'}《{src or '?'}》")
