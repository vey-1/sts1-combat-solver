"""Explainable potion choice over replay-validated routes, never executable commands."""
from collections import deque

from recovery import boss_heal_weight, effective_loss, hp_cost, hp_cost_table, hp_death


def key(value):
    return ''.join(c for c in value.lower() if c.isalnum())


def assess_deck(game):
    """Conservative weakness screen; these scores are not simulated win probabilities."""
    context = game.get('solver_auto_context', {})
    deck = context.get('deck', game.get('deck', []))
    ids = {key(c['id']) for c in deck}
    n = max(1, len(deck))
    # ponytail: static three-energy/draw-five profile misses combos and relic engines;
    # replace with sampled encounter rollouts once future-battle import is validated.
    damage = block = 0.0
    for c in deck:
        cost = max(1, c.get('cost', 1))
        hits = {'twinstrike': 2, 'swordboomerang': 3 + bool(c.get('upgrades')),
                'pummel': 4 + bool(c.get('upgrades'))}.get(key(c['id']), 1)
        damage += max(0, c.get('base_damage', 0)) * hits / cost
        block += max(0, c.get('base_block', 0)) / cost
    damage = round(damage * min(1, 5 / n), 1)
    block = round(block * min(1, 5 / n), 1)
    scaling = bool(ids & {'demonform', 'inflame', 'spotweakness', 'limitbreak'})
    scaling |= 'barricade' in ids and bool(ids & {'entrench', 'bodyslam'})
    scaling |= 'searingblow' in ids and any(key(c['id']) == 'searingblow' and c.get('upgrades', 0) >= 4 for c in deck)
    aoe = bool(ids & {'immolate', 'whirlwind', 'cleave', 'combust', 'reaper'})
    defense = block >= 6 or bool(ids & {'impervious', 'disarm', 'powerthrough', 'flamebarrier'})
    defense |= 'feelnopain' in ids and bool(ids & {'corruption', 'secondwind', 'fiendfire'})
    act = min(4, max(1, game.get('act', 1)))
    nodes = {(v['x'], v['y']): v for v in game.get('map', [])}
    start = (context.get('x'), context.get('y'))
    queue, seen, distance = deque([(start, 0)]), set(), None
    while queue:
        pos, steps = queue.popleft()
        if pos in seen:
            continue
        seen.add(pos)
        node = nodes.get(pos, {})
        if steps and node.get('symbol') == 'E':
            distance = steps
            break
        queue.extend(((c['x'], c['y']), steps + 1) for c in node.get('children', []))
    boss_distance = max(0, 15 - context['y']) if isinstance(context.get('y'), int) else None
    # At the boss there is no reason to reserve a bottle for that same fight.
    room = context.get('room', '')
    near_elite = distance is not None and distance <= 3
    near_boss = boss_distance is not None and 0 < boss_distance <= 4 and 'Boss' not in room
    weaknesses = []
    if damage < (9 if act == 1 else 13): weaknesses.append('爆发不足')
    if act >= 2 and not defense: weaknesses.append('防御不足')
    if not aoe: weaknesses.append('群攻不足')
    if not scaling: weaknesses.append('成长不足')
    threatened = (near_elite and bool(set(weaknesses) - {'成长不足'})) or (near_boss and bool(weaknesses))
    unknown = not context or not deck
    return dict(damage_per_draw=damage, block_per_draw=block, scaling=scaling, aoe=aoe,
                defense=defense, weaknesses=weaknesses, elite_distance=distance,
                boss_distance=boss_distance, boss=context.get('boss', ''),
                reserve=bool(threatened or unknown), unknown=unknown,
                summary=('牌组资料不足，保守留药' if unknown else
                         ('近期精英/BOSS：' if near_elite or near_boss else '牌组评估：')
                         + ('、'.join(weaknesses) or '未发现明显短板')) + '（启发式）')


def smoke_opening(game):
    """Solver policy is stricter than Java legality; missing history is not an opening."""
    context = game.get('solver_auto_context', {})
    return (context.get('smoke_opening') is True
            and game.get('combat_state', {}).get('turn') == 1
            and game.get('screen_type') == 'NONE')


def smoke_allowance(game):
    """(solver_smoke_allowance, solver_hp_cost) when both are valid, else None: the old raw-HP rule applies."""
    allowance, table = game.get('solver_smoke_allowance'), hp_cost_table(game)
    if (table is None or not isinstance(allowance, (int, float)) or isinstance(allowance, bool)
            or not allowance >= 0):
        return None
    return allowance, table


def smoke_threshold(game):
    """Raw-HP early stop for the native probe, which keeps the first win below it.

    With real-HP prices it is also capped by the smallest loss whose real cost exceeds the escape cost; it is never
    raised above smokeLossThreshold, or a generous allowance would stop the probe on a worse line.
    """
    threshold = game.get('solver_auto_context', {}).get('smoke_loss_threshold', 15)
    threshold = threshold if type(threshold) is int and threshold >= 0 else 15
    priced = smoke_allowance(game)
    if priced is not None:
        allowance, table = priced
        dear = next((k for k in range(1, len(table)) if hp_cost(table, k) > allowance), len(table))
        return min(threshold, dear)
    return threshold


def smoke_probe_keeps(plan, game):
    """Keep the Smoke Bomb and fight when the probe's potion-free win costs no more than escaping (docs/hp-value.md)."""
    if not (plan.get('outcome') == 1 and plan.get('hp', 0) > 0
            and not plan.get('new_permanent_curse')
            and not plan.get('used_fairy') and not plan.get('used_tail')):
        return False
    priced = smoke_allowance(game)
    if priced is not None:
        # solver_hp_cost is indexed by loss after this fight's victory heal, like health()['loss'].
        allowance, table = priced
        return hp_cost(table, max(0, game['current_hp'] - plan.get('post_hp', plan['hp']))) <= allowance
    # Old rule: raw combat loss, excluding future recovery credit and post-combat healing.
    return max(0, game['current_hp'] - plan['hp']) < smoke_threshold(game)


def potion_price_table(game):
    """solver_potion_price from the Mod's HpPrices (docs/hp-value.md), or None when missing or malformed.

    One entry per game['potions'] slot: a price in real HP, or None (empty slot, Fairy, Fruit Juice, Smoke Bomb).
    """
    table = game.get('solver_potion_price')
    if (not isinstance(table, list) or len(table) != len(game.get('potions', []))
            or not all(v is None or (isinstance(v, (int, float)) and not isinstance(v, bool) and v >= 0)
                       for v in table)):
        return None
    return table


def recommend(game, branches):
    profile = assess_deck(game)
    fallback = next((b for b in branches if b.get('available') and b.get('smoke_stop_loss')
                     and not b.get('new_permanent_curse')), None)
    if fallback and game.get('solver_auto_context', {}).get('run_auto') is True:
        return dict(branch=fallback['id'], reason='无药预算内未找到低战损胜线，执行T1烟雾止损候选', assessment=profile)
    context = game.get('solver_auto_context', {})
    relics = {r['id'] for r in game.get('relics', [])}
    probability = context.get('potion_drop_probability')
    if 'Sozu' in relics: probability = 0
    if 'White Beast Statue' in relics and 'Sozu' not in relics: probability = 1
    if context.get('potion_reward_blocked', 'Boss' in context.get('room', '')) is True: probability = 0
    potions = game.get('potions', [])
    full = bool(potions) and all(p['id'] != 'Potion Slot' for p in potions)
    supply = isinstance(probability, (int, float)) and probability >= .6
    guaranteed = probability == 1 and 'White Beast Statue' in relics and 'Sozu' not in relics
    winners = [b for b in branches if b.get('available') and b.get('won')]
    if not winners:
        return dict(branch=None, reason='未找到可验证的获胜路线，停止自动', assessment=profile)
    if any(m['id'] == 'CorruptHeart' for m in game.get('combat_state', {}).get('monsters', [])):
        return dict(branch=winners[0]['id'], reason='心脏：全部资源可用，找到存活胜线即执行', assessment=profile)
    clean = [b for b in winners if not b.get('new_permanent_curse')]
    if clean:
        winners = clean
    baseline = min((b for b in winners if not b.get('potion_uses')), key=lambda b: b['loss'], default=None)
    # Hand access and long-fight scaling: kept above the other strong bottles.
    premium = {'Swift Potion', 'GamblersBrew', 'DistilledChaos', 'HeartOfIron', 'CultistPotion'}
    strong = premium | {'GhostInAJar', 'DuplicationPotion',
              'Dexterity Potion', 'Strength Potion', 'PowerPotion',
              'SkillPotion', 'AttackPotion', 'ColorlessPotion', 'EntropicBrew', 'SneckoOil'}

    hp_weight = boss_heal_weight(game)
    heal_note = {1: '', .25: '；首领战后回血，战损按 1/4 计', 0: '；首领战后回满血，战损不计'}[hp_weight]

    prices = potion_price_table(game)

    def price(branch):
        total = 0
        for use in branch.get('potion_uses', []):
            potion = potions[use['slot']]['id']
            free_slot = branch.get('empty_potion_slots', 0) > 0
            if prices is not None and prices[use['slot']] is not None:
                # The tier already holds "keep it for a hard fight", so no reserve surcharge here.
                refill = (guaranteed or (supply and full)) and free_slot
                total += prices[use['slot']] * (.5 if refill else 1)
                continue
            valuable = potion in strong or (potion == 'Ancient Potion' and game.get('act') == 2)
            covers_gap = ((potion in {'Fire Potion', 'SteroidPotion', 'Energy Potion'} and '爆发不足' in profile['weaknesses'])
                          or (potion == 'Explosive Potion' and '群攻不足' in profile['weaknesses'])
                          or (potion in {'Block Potion', 'SpeedPotion'} and '防御不足' in profile['weaknesses']))
            reserve = profile['reserve'] and (valuable or covers_gap)
            # Full inventory discounts strong bottles too; otherwise it hoards
            # Strength/Dexterity even when the next drop is likely to be wasted.
            # A consumed bottle creates a real slot. A guaranteed refill lowers its
            # reserve cost even when the deck screen recommends keeping that type.
            top = potion in premium
            if guaranteed and free_slot:
                total += 8 if top else 5 if valuable else 3
            elif reserve:
                total += 25 if top else 20
            elif supply and full and free_slot:
                total += 8 if top else 5 if valuable else 3
            else:
                total += 15 if top else 10 if valuable else 5
        return total

    # A winning line consuming a revival is not equivalent to preserving it: a revival is priced as a death,
    # HpValue's price when the Mod exports it (the same max HP + 20).
    revival = hp_death(game)
    if revival is None:
        revival = game.get('max_hp', 80) + 20

    def score(b):
        return effective_loss(game, b) + price(b) + revival * (bool(b.get('used_tail')) + b.get('used_fairy', 0))

    chosen = min(winners, key=lambda b: (score(b), b['loss'], len(b.get('potion_uses', []))))
    saved = round(baseline['loss'] - chosen['loss'], 1) if baseline else None
    if chosen.get('potion_uses'):
        reason = '无药未找到胜线，优先保命' if baseline is None else f'省 {saved:g} 血；用药门槛 {price(chosen):g}'
        if baseline is not None: reason += heal_note
        if guaranteed and chosen.get('empty_potion_slots', 0) > 0: reason += '；白兽雕像，合法用药腾槽后可接战后药水'
        elif supply and full and chosen.get('empty_potion_slots', 0) > 0: reason += '；满栏且掉药概率高'
    else:
        reason = '保留药水；收益未超过用药/保留价值' + heal_note
    if chosen.get('used_tail') or chosen.get('used_fairy'):
        reason = '保命路线：消耗' + ('尾巴 ' if chosen.get('used_tail') else '') + (
            f"小精灵×{chosen['used_fairy']}" if chosen.get('used_fairy') else '')
    return dict(branch=chosen['id'], reason=reason, saved_hp=saved,
                drop_probability=probability, assessment=profile)
