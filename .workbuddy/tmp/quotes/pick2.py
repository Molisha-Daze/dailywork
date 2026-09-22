import json

THEME = ["劝学", "勤", "读书", "光阴", "少年", "少壮", "寸阴", "岁月", "青春", "努力",
         "奋斗", "志", "千里", "跬步", "积", "驽马", "锲", "磨", "坚", "自强", "不息",
         "勉", "励", "恒", "白首", "青春", "登", "高", "始", "功", "进", "勇", "敢",
         "问", "思", "知", "学", "行", "路", "山", "海", "春", "晨", "朝", "新"]

BAD = ["愁", "泪", "恨", "怨", "悲", "妾", "郎", "相思", "肠断", "酒", "醉",
       "孤", "寒", "空", "梦", "病", "别", "离", "罗", "鬓", "钗", "莺", "燕",
       "鸳", "枕", "香", "啼", "瘦", "纱", "烛", "凄", "黯", "销", "残", "暮",
       "灰", "情", "君", "伊", "断肠", "惆怅", "寂", "凉", "死"]
BAD_SRC = ["一言官方", "群", "贴吧", "百度", "知乎", "微博", "网易云", "网"]

hits = {"i": [], "k": []}
for c in ("i", "k"):
    for x in json.load(open(f"{c}.json", encoding="utf-8")):
        t = x["hitokoto"].strip()
        n = len(t)
        if not (8 <= n <= 30) or "\n" in t:
            continue
        src = (x.get("from") or "") + (x.get("from_who") or "")
        if any(w in t for w in BAD) or any(w in src for w in BAD_SRC):
            continue
        if not any(w in t for w in THEME):
            continue
        hits[c].append((n, t, x.get("from_who"), x.get("from")))

for c, name in (("i", "诗词"), ("k", "哲学")):
    print(f"===== {c} {name} 命中 {len(hits[c])} 条 =====")
    for n, t, who, src in sorted(set(hits[c]), key=lambda r: r[1]):
        print(f"{t} —— {who or ''}《{src or ''}》")
