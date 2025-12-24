# Bot AI Enhancement - Making Bots Feel Alive!

## Overview
This document describes the AI system that makes bot airlines behave realistically. Bot airlines now make intelligent, autonomous decisions about routes, pricing, and competition based on actual market conditions.

## ⭐ Phase 3: Demand-Based Intelligence (Current)

The AI system now makes decisions based on **actual demand data** and **dynamic market conditions**:

### Key Features

#### 1. **Demand-Based Route Selection**
- Routes are chosen based on **actual passenger demand** between airports
- Uses `DemandGenerator.computeBaseDemandBetweenAirports()` for real demand calculation
- Considers affinity, distance, population, and economic factors
- Scores routes by: `demand × competition_factor × personality_fit`
- Monopoly routes get 1.5x bonus, duopolies 1.0x, competitive markets 0.4x

#### 2. **Dynamic Pricing System**
- Prices are set based on:
  - **Competition level**: Monopoly → premium pricing, competitive → lower prices
  - **Estimated demand**: High demand → higher prices, low demand → discounts
  - **Competitor prices**: Analyzes competitor links and adjusts accordingly
  - **Personality**: Budget carriers always undercut, Premium charges more
- Prices bounded between 50% and 180% of standard fares

#### 3. **Continuous Price Optimization**
- 35% chance per cycle to review and adjust prices
- Monitors load factors on each route
- Raises prices when load factor > 95%
- Lowers prices when load factor < target
- Considers competitor pricing relative to own prices

#### 4. **Competitive Response System**
- 30% chance per cycle to analyze and respond to competition
- Different responses by personality:
  - **AGGRESSIVE**: Undercuts competitor prices by 5%
  - **BUDGET**: Always tries to be the cheapest (up to 10% below lowest competitor)
  - **PREMIUM**: Ignores budget competitors, focuses on quality
  - **CONSERVATIVE**: Makes moderate adjustments, prioritizes stability
  - **REGIONAL**: Avoids head-on competition, seeks niches
  - **BALANCED**: Tactical responses based on market position

#### 5. **Route Abandonment**
- 15% chance per cycle to evaluate route profitability
- Abandons routes after 4 consecutive unprofitable cycles
- Also abandons routes with <30% average load factor
- Personality affects abandonment threshold:
  - Aggressive: Very reluctant (needs <20% LF to abandon)
  - Conservative: Quick to cut losses (<40% LF threshold)
  - Budget: Needs good utilization (<35% LF threshold)

## Features Implemented

### 1. **Bot Personality System**
Each bot airline now has a distinct personality that drives its decision-making:

#### **AGGRESSIVE** 🚀
- **Strategy**: Rapid expansion, high-risk/high-reward
- **Behavior**: 
  - Targets routes with 500K+ population
  - Prefers long-haul international routes
  - High frequency operations (60-90% capacity target)
  - Spends 25% of cash on fleet expansion
- **Competition**: Increases frequency to dominate routes
- **Example**: Think Ryanair, Southwest

#### **CONSERVATIVE** 🏛️
- **Strategy**: Slow, steady, profitable growth
- **Behavior**:
  - Only operates from major hubs (size 5+, 2M+ population)
  - Maintains 75-95% capacity utilization
  - Careful fleet management (15% budget)
  - Focuses on established, proven routes
- **Competition**: Maintains service quality, avoids price wars
- **Example**: Think legacy carriers like British Airways

#### **BALANCED** ⚖️
- **Strategy**: Adaptable, well-rounded approach
- **Behavior**:
  - Medium-sized airports (1M+ population)
  - 70-88% capacity target
  - Mix of short/long-haul routes
  - 18% fleet budget
- **Competition**: Responds tactically based on situation
- **Example**: Think Emirates, Singapore Airlines

#### **REGIONAL** 🏔️
- **Strategy**: Connect smaller communities
- **Behavior**:
  - Targets smaller airports (size 2-4, 100K+ population)
  - Prefers domestic/regional routes (<2000km)
  - High same-country route bonus
  - Regional aircraft focus
- **Competition**: Finds niche markets, avoids direct competition
- **Example**: Think regional carriers like Alaska Airlines

#### **PREMIUM** 💎
- **Strategy**: Luxury service, premium pricing
- **Behavior**:
  - Major hubs only (size 6+, 5M+ population)
  - Long-haul premium routes (5000km+)
  - Lower capacity target (70-85%) - quality over quantity
  - High-income market focus
  - Wide-body aircraft preference
- **Competition**: Maintains service advantage, ignores budget competitors
- **Example**: Think Qatar Airways, Singapore Airlines First Class

#### **BUDGET** 💰
- **Strategy**: Low-cost carrier model
- **Behavior**:
  - High-demand, short-haul routes (<3000km)
  - Very high utilization (80-98% capacity)
  - Low fleet spending (12% budget)
  - Single-aisle aircraft focus
- **Competition**: Aggressive price competition
- **Example**: Think Ryanair, EasyJet, Spirit

### 2. **Intelligent Decision-Making**

#### **Route Planning** (15% chance per cycle)
- Analyzes potential destinations based on personality
- Considers:
  - Airport size and population
  - Distance and route profitability
  - Existing competition
  - Available cash/aircraft
- Scores destinations using personality-specific algorithms
- Selects top 5 candidates for expansion

#### **Fleet Management** (20% chance per cycle)
- Evaluates current fleet age and composition
- Determines needed aircraft category
- Considers budget constraints
- Plans purchases based on personality:
  - Aggressive: Focus on growth (regional → large)
  - Conservative: Replace aging aircraft first
  - Budget: Efficient single-aisle aircraft
  - Premium: Wide-body for long-haul
  - Regional: Regional jets and turboprops

#### **Route Optimization** (10% chance per cycle)
- Monitors capacity utilization on existing routes
- Adjusts frequencies based on demand:
  - **Over-capacity** (>target high): Increase frequency
  - **Under-capacity** (<target low): Decrease frequency
- Improves profitability by right-sizing operations
- Personality-specific capacity targets guide decisions

#### **Competition Response** (25% chance per cycle)
- Detects competitors on shared routes
- Responds based on personality:
  - **Aggressive**: Increase frequency, price war
  - **Budget**: Lower prices to compete
  - **Premium**: Maintain quality, ignore budget competitors
  - **Conservative**: Steady course, focus on service
  - **Regional**: Find alternative niches
- Creates dynamic market conditions

### 3. **Seamless Integration**
- Runs every simulation cycle (weekly)
- Processes all bot airlines (`AirlineType.NON_PLAYER`)
- No visual indicators needed - bots behave like regular players
- Player experiences organic competition
- No performance impact - efficient probability-based execution

## How It Works

### Simulation Flow
```
Every Cycle (Weekly):
  ├── Load all bot airlines
  ├── Determine personality for each bot
  ├── For each bot:
  │   ├── 15% chance → Plan new routes
  │   ├── 20% chance → Purchase aircraft
  │   ├── 10% chance → Optimize routes
  │   └── 25% chance → Respond to competition
  └── Bots make decisions autonomously
```

### Personality Assignment
```scala
Cash > $1B + High Service Quality → PREMIUM
Cash > $1B + Lower Quality → AGGRESSIVE
Cash > $100M → BALANCED
High Reputation → CONSERVATIVE
Default → REGIONAL
Airline Type DISCOUNT → BUDGET
Airline Type LUXURY → PREMIUM
```

## Player Experience

### What Players Will Notice:

1. **Dynamic Competition**
   - Bot airlines expand into profitable markets
   - Compete on routes with frequency/price adjustments
   - React to player's actions

2. **Realistic Behavior**
   - Some bots are aggressive expanders
   - Others focus on premium service
   - Budget carriers fight on price
   - Regional carriers serve smaller markets

3. **Market Evolution**
   - Routes get more competitive over time
   - New routes appear as bots expand
   - Unprofitable routes get reduced/cancelled
   - Fleet composition changes over time

4. **Strategic Depth**
   - Players must adapt to bot strategies
   - Can't rely on static competition
   - Market conditions evolve organically
   - Different personalities create varied challenges

## Future Enhancements (Phase 2)

### Short-term:
- [ ] Actual route creation logic (currently placeholder)
- [ ] Aircraft purchase implementation
- [ ] Frequency adjustment mechanics
- [ ] Price competition system

### Medium-term:
- [ ] Alliance formation between bots
- [ ] Hub strategy (wave scheduling)
- [ ] Code-share agreements
- [ ] Marketing campaigns

### Long-term:
- [ ] Machine learning from player strategies
- [ ] Adaptive difficulty based on player skill
- [ ] Historical "memory" of successful strategies
- [ ] Cooperative/competitive bot relationships

## Technical Details

### Files Created:
- `airline-data/src/main/scala/com/patson/BotAISimulation.scala` - Core AI engine

### Files Modified:
- `airline-data/src/main/scala/com/patson/MainSimulation.scala` - Integration into cycle

### Dependencies:
- Uses existing data sources (AirlineSource, LinkSource, etc.)
- No new database tables required
- Compatible with current simulation architecture

### Performance:
- Probability-based execution prevents overwhelming simulation
- Each bot only performs 0-4 actions per cycle
- Efficient scoring algorithms
- No impact on cycle time

## Configuration

### Tuning Probabilities (Phase 3):
```scala
val ROUTE_PLANNING_PROBABILITY = 0.20        // 20% chance - plan new demand-based routes
val AIRPLANE_PURCHASE_PROBABILITY = 0.20     // 20% chance - buy new aircraft
val ROUTE_OPTIMIZATION_PROBABILITY = 0.35    // 35% chance - optimize prices dynamically
val COMPETITION_RESPONSE_PROBABILITY = 0.30  // 30% chance - respond to competitors
val ROUTE_ABANDONMENT_PROBABILITY = 0.15     // 15% chance - evaluate unprofitable routes
```

Adjust these values in `BotAISimulation.scala` to control bot activity levels.

### Pricing Constants:
```scala
val MIN_PRICE_MULTIPLIER = 0.50  // Never go below 50% of standard price
val MAX_PRICE_MULTIPLIER = 1.80  // Never go above 180% of standard price
val PRICE_ADJUSTMENT_STEP = 0.05 // 5% price adjustment per cycle
```

### Personality Price Multipliers:
Each personality has a base price multiplier:
- **AGGRESSIVE**: 0.92 (8% below market)
- **BUDGET**: 0.72 (28% below market - deep discounts!)
- **BALANCED**: 1.0 (market rate)
- **REGIONAL**: 0.95 (5% below market)
- **CONSERVATIVE**: 1.12 (12% above market)
- **PREMIUM**: 1.35 (35% above market)

### Additional Personality Parameters:
- `minAirportSize` - Minimum airport size to consider
- `minPopulation` - Minimum population requirement
- `targetCapacityLow/High` - Ideal capacity utilization range
- `fleetBudgetRatio` - Percentage of cash for fleet purchases

## Testing

### Verify Bot Activity:
1. Check simulation logs for "[Bot AI Simulation - Phase 3]" messages
2. Look for "📊 Demand:" logs showing demand-based route selection
3. Watch for "📈 RAISED" / "📉 LOWERED" price adjustment messages
4. Monitor competitive responses (⚔️ AGGRESSIVE, 💸 BUDGET, etc.)
5. Check for "❌ ABANDONED" routes being cut

### Expected Behavior:
- Bots select routes based on ACTUAL demand between airports
- Prices adjust dynamically based on load factor (35% of cycles)
- Competition triggers price wars (30% of cycles)
- Unprofitable routes get abandoned after 4 cycles
- Different personalities create diverse market behaviors

## Summary

This system transforms bot airlines from passive entities into intelligent, personality-driven competitors that:
- ✅ Select routes based on **actual passenger demand**
- ✅ Set prices dynamically based on **competition and demand**
- ✅ Adjust prices continuously as market conditions change
- ✅ Respond strategically to competitor pricing
- ✅ Abandon unprofitable routes
- ✅ Create dynamic, realistic airline competition

---

**Status**: Phase 3 Complete - Full demand-based intelligence with dynamic pricing
**Impact**: Bots now compete realistically, making gameplay challenging and engaging
**Features**: Demand-based routes, dynamic pricing, competition response, route abandonment
