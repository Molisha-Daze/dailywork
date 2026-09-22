import json, collections, random

random.seed(42)
for c, name in [("i", "诗词"), ("k", "哲学"), ("d", "文学")]:
    data = json.load(open(f"{c}.json", encoding="utf-8"))
    who = sum(1 for x in data if x.get("from_who"))
    lens = [len(x["hitokoto"]) for x in data]
    print(f"--- {c} {name}: {len(data)} 条, 有作者 {who} ({who*100//len(data)}%), "
          f"长度 min={min(lens)} 中位={sorted(lens)[len(lens)//2]} max={max(lens)}")
    bucket = collections.Counter()
    for L in lens:
        bucket[min(L // 10 * 10, 50)] += 1
    print("    长度分布:", dict(sorted(bucket.items())))
    for x in random.sample(data, 6):
        print(f"    [{len(x['hitokoto']):3d}] {x['hitokoto']}  —— {x.get('from_who') or '?'}《{x.get('from') or '?'}》")
