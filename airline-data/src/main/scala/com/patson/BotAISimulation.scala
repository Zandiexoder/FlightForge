package com.patson

import com.patson.data._
import com.patson.data.airplane.ModelSource
import com.patson.model._
import com.patson.model.airplane._
import com.patson.util.{AirlineCache, AirportCache}

import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.util.Random

/**
 * Bot AI Simulation - Makes bot airlines feel alive by giving them intelligent decision-making
 * 
 * PHASE 3: Demand-based route selection, dynamic pricing, and realistic behaviors
 * 
 * Key Features:
 * - Routes are selected based on ACTUAL DEMAND between airports
 * - Prices are set dynamically based on competition and demand
 * - Prices adjust over time as market conditions change
 * - Unprofitable routes are abandoned
 * - Bots monitor and respond to competitor actions
 */
object BotAISimulation {
  
  val ROUTE_PLANNING_PROBABILITY = 0.20 // 20% chance per cycle to plan new routes
  val AIRPLANE_PURCHASE_PROBABILITY = 0.20 // 20% chance per cycle to buy planes
  val ROUTE_OPTIMIZATION_PROBABILITY = 0.35 // 35% chance to optimize existing routes (increased - this handles pricing!)
  val COMPETITION_RESPONSE_PROBABILITY = 0.30 // 30% chance to respond to competition
  val ROUTE_ABANDONMENT_PROBABILITY = 0.15 // 15% chance to evaluate route abandonment
  
  val MAX_ROUTES_PER_CYCLE = 2 // Maximum new routes per cycle per bot
  val MAX_AIRCRAFT_PURCHASE = 3 // Maximum aircraft purchases per cycle per bot
  
  // Pricing strategy constants
  val MIN_PRICE_MULTIPLIER = 0.50 // Never go below 50% of standard price
  val MAX_PRICE_MULTIPLIER = 1.80 // Never go above 180% of standard price
  val PRICE_ADJUSTMENT_STEP = 0.05 // 5% price adjustment per cycle
  
  // Route profitability thresholds
  val UNPROFITABLE_CYCLES_THRESHOLD = 4 // Abandon route after 4 unprofitable cycles
  val MIN_LOAD_FACTOR_THRESHOLD = 0.30 // Minimum acceptable load factor
  
  def simulate(cycle: Int): Unit = {
    println("============================================")
    println("Starting Bot AI Simulation - Phase 3")
    println("Demand-Based Routes | Dynamic Pricing")
    println("============================================")
    
    val botAirlines = AirlineSource.loadAllAirlines(fullLoad = true)
      .filter(_.airlineType == AirlineType.NON_PLAYER)
    
    if (botAirlines.isEmpty) {
      println("No bot airlines found, skipping simulation")
      return
    }
    
    println(s"Processing ${botAirlines.size} bot airlines")
    
    // Pre-load market data for efficiency
    val allAirports = AirportSource.loadAllAirports(fullLoad = true)
    val countryRelationships = CountrySource.getCountryMutualRelationships()
    
    botAirlines.foreach { airline =>
      try {
        val personality = determineBotPersonality(airline)
        println(s"\n[${airline.name}] Personality: $personality | Balance: $$${airline.getBalance()/1000000}M")
        
        // Route planning - add new routes based on DEMAND
        if (Random.nextDouble() < ROUTE_PLANNING_PROBABILITY) {
          planNewRoutes(airline, personality, cycle, allAirports, countryRelationships)
        }
        
        // Route optimization - CRITICAL: adjust pricing based on load factor & competition
        if (Random.nextDouble() < ROUTE_OPTIMIZATION_PROBABILITY) {
          optimizeExistingRoutes(airline, personality, cycle)
        }
        
        // Competition response - react to player/other bots
        if (Random.nextDouble() < COMPETITION_RESPONSE_PROBABILITY) {
          respondToCompetition(airline, personality, cycle)
        }
        
        // Route abandonment - cut unprofitable routes
        if (Random.nextDouble() < ROUTE_ABANDONMENT_PROBABILITY) {
          evaluateRouteAbandonment(airline, personality, cycle)
        }
        
        // Fleet management - buy new airplanes
        if (Random.nextDouble() < AIRPLANE_PURCHASE_PROBABILITY) {
          purchaseAirplanes(airline, personality, cycle)
        }
        
      } catch {
        case e: Exception =>
          println(s"Error processing bot airline ${airline.name}: ${e.getMessage}")
          e.printStackTrace()
      }
    }
    
    println("\n============================================")
    println("Finished Bot AI Simulation")
    println("============================================\n")
  }
  
  /**
   * Determine bot personality based on airline characteristics
   */
  private def determineBotPersonality(airline: Airline): BotPersonality = {
    val cash = airline.getBalance()
    val reputation = airline.getReputation()
    val serviceQuality = airline.getCurrentServiceQuality()
    
    // Use airline type as base personality
    airline.airlineType match {
      case AirlineType.DISCOUNT => BotPersonality.BUDGET
      case AirlineType.LUXURY => BotPersonality.PREMIUM
      case _ =>
        // Determine by characteristics
        if (cash > 1000000000) { // > $1B
          if (serviceQuality > 40) BotPersonality.PREMIUM
          else BotPersonality.AGGRESSIVE
        } else if (cash > 100000000) { // > $100M
          BotPersonality.BALANCED
        } else if (reputation > 60) {
          BotPersonality.CONSERVATIVE
        } else {
          BotPersonality.REGIONAL
        }
    }
  }
  
  /**
   * Plan new routes based on bot personality - PHASE 3: DEMAND-BASED route selection!
   */
  private def planNewRoutes(
    airline: Airline, 
    personality: BotPersonality, 
    cycle: Int,
    allAirports: List[Airport],
    countryRelationships: Map[(String, String), Int]
  ): Unit = {
    println(s"[${airline.name}] Planning new routes based on DEMAND (${personality})")
    
    val bases = AirlineSource.loadAirlineBasesByAirline(airline.id)
    if (bases.isEmpty) {
      println(s"[${airline.name}] No bases found, cannot plan routes")
      return
    }
    
    val existingLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val existingDestinations = existingLinks.flatMap(link => List(link.from.id, link.to.id)).toSet
    
    // Get available cash for route expansion
    val availableCash = airline.getBalance() * 0.1 // Use 10% of cash for expansion
    if (availableCash < 5000000) {
      println(s"[${airline.name}] Insufficient funds for expansion (need $$5M)")
      return
    }
    
    // Get available airplanes
    val allAirplanes = AirplaneSource.loadAirplanesByOwner(airline.id)
    val assignedAirplanes = LinkSource.loadFlightLinksByAirlineId(airline.id)
      .flatMap(_.getAssignedAirplanes().keys)
      .toSet
    val availableAirplanes = allAirplanes.filter(a => 
      !assignedAirplanes.contains(a) && a.isReady
    )
    
    if (availableAirplanes.isEmpty) {
      println(s"[${airline.name}] No available aircraft for new routes")
      return
    }
    
    var routesCreated = 0
    
    bases.foreach { base =>
      if (routesCreated >= MAX_ROUTES_PER_CYCLE) return
      
      // Find routes with HIGH DEMAND
      val potentialDestinations = findDemandBasedDestinations(
        base.airport, 
        existingDestinations, 
        personality, 
        availableCash.toLong,
        availableAirplanes,
        allAirports,
        countryRelationships
      )
      
      potentialDestinations.foreach { case (destination, estimatedDemand, competitorCount) =>
        if (routesCreated < MAX_ROUTES_PER_CYCLE) {
          // Find suitable aircraft for this route
          val distance = Computation.calculateDistance(base.airport, destination).intValue()
          val suitableAircraft = availableAirplanes.find(airplane => 
            airplane.model.range >= distance && 
            airplane.model.runwayRequirement <= Math.min(base.airport.runwayLength, destination.runwayLength)
          )
          
          suitableAircraft match {
            case Some(airplane) =>
              // Calculate optimal frequency based on demand and personality
              val frequency = calculateDemandBasedFrequency(distance, airplane, estimatedDemand, personality)
              
              // Create the link with DYNAMIC PRICING based on competition!
              val success = createRouteWithDynamicPricing(
                airline,
                base.airport,
                destination,
                airplane,
                frequency,
                personality,
                cycle,
                estimatedDemand,
                competitorCount
              )
              
              if (success) {
                routesCreated += 1
                println(s"✈️  [${airline.name}] NEW ROUTE: ${base.airport.iata} -> ${destination.iata}")
                println(s"    📊 Demand: $estimatedDemand pax/week | Competitors: $competitorCount | Freq: ${frequency}x weekly")
              }
              
            case None =>
              println(s"[${airline.name}] No suitable aircraft for ${base.airport.iata} -> ${destination.iata} (${distance}km)")
          }
        }
      }
    }
    
    if (routesCreated == 0) {
      println(s"[${airline.name}] No new routes created this cycle")
    }
  }
  
  /**
   * Actually create a route link - PHASE 3: Dynamic pricing based on competition and demand!
   */
  private def createRouteWithDynamicPricing(
    airline: Airline,
    from: Airport,
    to: Airport,
    airplane: Airplane,
    frequency: Int,
    personality: BotPersonality,
    cycle: Int,
    estimatedDemand: Int,
    competitorCount: Int
  ): Boolean = {
    try {
      val distance = Computation.calculateDistance(from, to).intValue()
      val duration = Computation.calculateDuration(airplane.model, distance)
      
      // Calculate DYNAMIC pricing based on competition and demand!
      val pricingMap = calculateDynamicPricing(from, to, distance, personality, competitorCount, estimatedDemand, airplane.model.capacity * frequency)
      
      // Create link class configuration based on personality
      val linkClassConfig = personality.configureLinkClasses(airplane)
      
      // Create the link
      val link = Link(
        from,
        to,
        airline,
        LinkClassValues.getInstance(
          pricingMap(ECONOMY).toInt, 
          pricingMap(BUSINESS).toInt, 
          pricingMap(FIRST).toInt
        ), // Pricing
        distance,
        LinkClassValues.getInstanceByMap(linkClassConfig), // Capacity configuration
        personality.serviceQuality.toInt, // rawQuality
        duration,
        frequency,
        0 // flightNumber
      )
      
      // Assign airplane to link
      link.setAssignedAirplanes(Map(airplane -> LinkAssignment(frequency, frequency)))
      
      // Save the link
      LinkSource.saveLink(link) match {
        case Some(savedLink) =>
          val avgCompetitorPrice = if (competitorCount > 0) "competing" else "monopoly"
          println(s"    💰 Dynamic Pricing ($avgCompetitorPrice market):")
          println(s"       Economy: $$${pricingMap(ECONOMY).toInt} | Business: $$${pricingMap(BUSINESS).toInt} | First: $$${pricingMap(FIRST).toInt}")
          true
        case None =>
          println(s"    ❌ Failed to save link")
          false
      }
      
    } catch {
      case e: Exception =>
        println(s"    ❌ Error creating route: ${e.getMessage}")
        false
    }
  }
  
  /**
   * Calculate dynamic pricing based on competition and demand
   * This is the CORE of intelligent pricing!
   */
  private def calculateDynamicPricing(
    fromAirport: Airport,
    toAirport: Airport,
    distance: Int,
    personality: BotPersonality,
    competitorCount: Int,
    estimatedDemand: Int,
    ourCapacity: Int
  ): Map[LinkClass, Double] = {
    
    val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
    val baseIncome = fromAirport.baseIncome
    
    // Get standard prices as baseline
    val standardEconomy = Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome).toDouble
    val standardBusiness = Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome).toDouble
    val standardFirst = Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome).toDouble
    
    // Load existing competitor links to analyze their pricing
    val competitorLinks = (LinkSource.loadFlightLinksByAirports(fromAirport.id, toAirport.id) ++ 
                          LinkSource.loadFlightLinksByAirports(toAirport.id, fromAirport.id))
    
    // Calculate competition multiplier
    val competitionMultiplier = if (competitorCount == 0) {
      // MONOPOLY - can charge premium!
      personality match {
        case BotPersonality.PREMIUM => 1.40  // Premium charges high
        case BotPersonality.AGGRESSIVE => 1.15 // Aggressive takes some premium
        case BotPersonality.BUDGET => 0.85   // Budget still prices low to build market
        case _ => 1.20 // Others take moderate premium
      }
    } else if (competitorCount == 1) {
      // DUOPOLY - moderate competition
      personality match {
        case BotPersonality.BUDGET => 0.80
        case BotPersonality.AGGRESSIVE => 0.90
        case BotPersonality.PREMIUM => 1.20
        case _ => 1.0
      }
    } else {
      // COMPETITIVE MARKET - price based on competition
      val avgCompetitorEconomy = if (competitorLinks.nonEmpty) {
        competitorLinks.map(_.price(ECONOMY)).sum.toDouble / competitorLinks.size
      } else standardEconomy
      
      // Price relative to competitors
      personality match {
        case BotPersonality.BUDGET => 0.75   // Undercut significantly
        case BotPersonality.AGGRESSIVE => 0.88 // Undercut slightly
        case BotPersonality.PREMIUM => 1.10   // Price above for quality
        case BotPersonality.CONSERVATIVE => 1.0 // Match market
        case _ => 0.95
      }
    }
    
    // Calculate demand multiplier
    // If demand >> capacity, we can charge more
    // If demand << capacity, we need to lower prices
    val demandRatio = if (ourCapacity > 0) estimatedDemand.toDouble / ourCapacity else 1.0
    val demandMultiplier = if (demandRatio > 2.0) {
      1.15 // High demand - increase prices
    } else if (demandRatio > 1.5) {
      1.08
    } else if (demandRatio < 0.5) {
      0.85 // Low demand - decrease prices
    } else if (demandRatio < 0.8) {
      0.92
    } else {
      1.0
    }
    
    // Apply multipliers with personality base adjustment
    val personalityBase = personality.priceMultiplier
    val finalMultiplier = Math.max(MIN_PRICE_MULTIPLIER, 
                          Math.min(MAX_PRICE_MULTIPLIER, 
                                   personalityBase * competitionMultiplier * demandMultiplier))
    
    Map(
      ECONOMY -> (standardEconomy * finalMultiplier),
      BUSINESS -> (standardBusiness * finalMultiplier * 1.05), // Business slightly higher
      FIRST -> (standardFirst * finalMultiplier * 1.10) // First class premium
    )
  }
  
  /**
   * Calculate frequency based on actual demand
   */
  private def calculateDemandBasedFrequency(
    distance: Int, 
    airplane: Airplane, 
    estimatedDemand: Int,
    personality: BotPersonality
  ): Int = {
    val seatsPerFlight = airplane.model.capacity
    
    // Target utilization based on personality
    val targetLoadFactor = (personality.targetCapacityLow + personality.targetCapacityHigh) / 2
    
    // Calculate how many flights needed to meet demand at target load factor
    val flightsNeededForDemand = if (seatsPerFlight > 0) {
      (estimatedDemand / (seatsPerFlight * targetLoadFactor)).toInt
    } else 1
    
    // Factor in distance (longer routes = fewer flights possible)
    val distanceFactor = if (distance > 8000) 0.5
                         else if (distance > 5000) 0.7
                         else if (distance > 2000) 0.85
                         else 1.0
    
    // Calculate final frequency
    val calculatedFrequency = Math.max(1, (flightsNeededForDemand * distanceFactor).toInt)
    
    // Personality adjustments
    val personalityAdjusted = personality match {
      case BotPersonality.AGGRESSIVE => Math.min(calculatedFrequency + 2, 21)
      case BotPersonality.BUDGET => Math.min(calculatedFrequency + 3, 28) // LCCs love high frequency
      case BotPersonality.PREMIUM => Math.max(1, calculatedFrequency - 1) // Premium = fewer, bigger
      case _ => calculatedFrequency
    }
    
    // Cap frequency reasonably
    Math.min(personalityAdjusted, 21) // Max 3 flights per day
  }
  
  /**
   * Find destinations based on ACTUAL DEMAND between airports
   */
  private def findDemandBasedDestinations(
    fromAirport: Airport,
    existingDestinations: Set[Int],
    personality: BotPersonality,
    budget: Long,
    availableAircraft: List[Airplane],
    allAirports: List[Airport],
    countryRelationships: Map[(String, String), Int]
  ): List[(Airport, Int, Int)] = { // Returns (Airport, EstimatedDemand, CompetitorCount)
    
    if (availableAircraft.isEmpty) return List.empty
    
    val maxRange = availableAircraft.map(_.model.range).max
    val minRunway = availableAircraft.map(_.model.runwayRequirement).min
    
    // Filter viable airports
    val viableAirports = allAirports.filter(airport => 
      !existingDestinations.contains(airport.id) &&
      airport.id != fromAirport.id &&
      airport.size >= personality.minAirportSize &&
      airport.population >= personality.minPopulation &&
      airport.runwayLength >= minRunway
    )
    
    // Score airports by ACTUAL DEMAND
    val scoredAirports = viableAirports.flatMap { toAirport =>
      val distance = Computation.calculateDistance(fromAirport, toAirport).intValue()
      
      if (distance <= maxRange && distance > DemandGenerator.MIN_DISTANCE) {
        // Calculate actual demand between these airports!
        val relationship = countryRelationships.getOrElse((fromAirport.countryCode, toAirport.countryCode), 0)
        val affinity = Computation.calculateAffinityValue(fromAirport.zone, toAirport.zone, relationship)
        
        val demand = DemandGenerator.computeBaseDemandBetweenAirports(fromAirport, toAirport, affinity, distance)
        val totalDemand = DemandGenerator.addUpDemands(demand)
        
        // Check competition on this route
        val competitorLinks = LinkSource.loadFlightLinksByAirports(fromAirport.id, toAirport.id) ++
                             LinkSource.loadFlightLinksByAirports(toAirport.id, fromAirport.id)
        val competitorCount = competitorLinks.size
        
        // Calculate attractiveness score
        // High demand + low competition = very attractive!
        val competitionPenalty = competitorCount match {
          case 0 => 1.5   // Monopoly opportunity!
          case 1 => 1.0   // Duopoly
          case 2 => 0.7   // Getting crowded
          case _ => 0.4   // Very competitive
        }
        
        // Personality-based scoring adjustments
        val personalityScore = personality.scoreDestination(toAirport, distance, fromAirport)
        
        // Combined score: demand * competition factor * personality fit
        val finalScore = totalDemand * competitionPenalty * (personalityScore / 100.0)
        
        if (totalDemand > 50) { // Minimum demand threshold
          Some((toAirport, totalDemand, competitorCount, finalScore))
        } else None
      } else None
    }
    
    // Return top candidates sorted by score
    scoredAirports
      .sortBy(-_._4)
      .take(5)
      .map(t => (t._1, t._2, t._3)) // (Airport, Demand, CompetitorCount)
  }
  
  /**
   * Purchase airplanes based on personality and needs
   */
  private def purchaseAirplanes(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Considering airplane purchases (${personality})")
    
    val availableCash = airline.getBalance() * personality.fleetBudgetRatio
    if (availableCash < 5000000) return // Need at least $5M
    
    val currentFleet = AirplaneSource.loadAirplanesByOwner(airline.id)
    val avgAge = if (currentFleet.nonEmpty) {
      currentFleet.map(a => cycle - a.purchasedCycle).sum / currentFleet.size
    } else 0
    
    // Determine what type of aircraft is needed
    val neededCategory = personality.preferredAircraftCategory(currentFleet, avgAge)
    
    println(s"[${airline.name}] Looking for ${neededCategory} aircraft, budget: $$${availableCash/1000000}M")
    
    // TODO: Actual aircraft purchase logic
    // Would need to:
    // 1. Find suitable models in category
    // 2. Check if affordable
    // 3. Consider age vs new purchase
    // 4. Configure based on personality
    // 5. Purchase and assign home base
  }
  
    /**
   * Optimize existing routes - PHASE 3: DYNAMIC price and frequency adjustments!
   * This is called frequently to keep pricing competitive
   */
  private def optimizeExistingRoutes(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Optimizing routes with dynamic pricing...")
    
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    if (links.isEmpty) {
      println(s"[${airline.name}] No routes to optimize")
      return
    }
    
    // Load consumption data for performance analysis
    val linkConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = linkConsumptions.groupBy(_.link.id)
    
    var priceAdjustments = 0
    var frequencyAdjustments = 0
    
    links.foreach { link =>
      // Get last cycle's performance
      val lastConsumption = consumptionByLink.get(link.id).flatMap(_.headOption)
      
      lastConsumption match {
        case Some(consumption) =>
          // Calculate load factor
          val totalCapacity = link.capacity.total * link.frequency
          val soldSeats = consumption.link.soldSeats.total
          val loadFactor = if (totalCapacity > 0) soldSeats.toDouble / totalCapacity else 0.0
          
          // Get current competition on this route
          val competitorLinks = (LinkSource.loadFlightLinksByAirports(link.from.id, link.to.id) ++
                                LinkSource.loadFlightLinksByAirports(link.to.id, link.from.id))
                                .filter(_.airline.id != airline.id)
          val competitorCount = competitorLinks.size
          
          // Analyze competitor pricing
          val avgCompetitorPrice = if (competitorLinks.nonEmpty) {
            competitorLinks.map(_.price(ECONOMY)).sum.toDouble / competitorLinks.size
          } else link.price(ECONOMY).toDouble
          
          val ourPrice = link.price(ECONOMY)
          val priceRatio = ourPrice.toDouble / avgCompetitorPrice
          
          // Decide on price adjustment
          val newPricing = calculatePriceAdjustment(
            link, 
            loadFactor, 
            priceRatio, 
            competitorCount, 
            personality,
            consumption.profit
          )
          
          // Apply price changes if significant
          if (newPricing != link.price) {
            val updatedLink = link.copy(price = newPricing)
            updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
            LinkSource.updateLink(updatedLink)
            
            val changeType = if (newPricing(ECONOMY) > link.price(ECONOMY)) "📈 RAISED" else "📉 LOWERED"
            println(s"    $changeType prices on ${link.from.iata}->${link.to.iata}: " +
                   s"$$${link.price(ECONOMY)} → $$${newPricing(ECONOMY)} (LF: ${(loadFactor*100).toInt}%, Competitors: $competitorCount)")
            priceAdjustments += 1
          }
          
          // Frequency adjustments based on load factor
          if (loadFactor > personality.targetCapacityHigh && link.frequency < 21) {
            // Route is full - increase frequency if we have aircraft
            println(s"    📊 ${link.from.iata}->${link.to.iata} is running hot (${(loadFactor*100).toInt}% LF) - consider frequency increase")
            frequencyAdjustments += 1
          } else if (loadFactor < personality.targetCapacityLow && link.frequency > 1) {
            // Route is underperforming
            println(s"    ⚠️  ${link.from.iata}->${link.to.iata} is underperforming (${(loadFactor*100).toInt}% LF)")
          }
          
        case None =>
          // No consumption data yet - new route, leave pricing as is
      }
    }
    
    if (priceAdjustments > 0) {
      println(s"[${airline.name}] Adjusted prices on $priceAdjustments routes")
    }
  }
  
  /**
   * Calculate price adjustment based on load factor and competition
   */
  private def calculatePriceAdjustment(
    link: Link,
    loadFactor: Double,
    priceRatioToCompetitors: Double,
    competitorCount: Int,
    personality: BotPersonality,
    profit: Long
  ): LinkClassValues = {
    
    val currentEconomy = link.price(ECONOMY)
    val currentBusiness = link.price(BUSINESS)
    val currentFirst = link.price(FIRST)
    
    // Get standard prices for comparison
    val flightCategory = Computation.getFlightCategory(link.from, link.to)
    val standardEconomy = Pricing.computeStandardPrice(link.distance, flightCategory, ECONOMY, PassengerType.TRAVELER, link.from.baseIncome)
    
    var multiplier = 1.0
    
    // Load factor based adjustments
    if (loadFactor > 0.95) {
      // Nearly full - can raise prices
      multiplier += PRICE_ADJUSTMENT_STEP * 2
    } else if (loadFactor > personality.targetCapacityHigh) {
      // Above target - slight increase
      multiplier += PRICE_ADJUSTMENT_STEP
    } else if (loadFactor < personality.targetCapacityLow) {
      // Below target - need to lower prices
      multiplier -= PRICE_ADJUSTMENT_STEP * 2
    } else if (loadFactor < personality.targetCapacityLow + 0.1) {
      // Slightly below target
      multiplier -= PRICE_ADJUSTMENT_STEP
    }
    
    // Competition based adjustments
    if (competitorCount > 0) {
      if (priceRatioToCompetitors > 1.2) {
        // We're much more expensive - lower prices unless premium
        if (personality != BotPersonality.PREMIUM) {
          multiplier -= PRICE_ADJUSTMENT_STEP
        }
      } else if (priceRatioToCompetitors < 0.8 && loadFactor > 0.8) {
        // We're cheaper and still filling up - can raise prices
        multiplier += PRICE_ADJUSTMENT_STEP
      }
    } else {
      // Monopoly - if profitable, slowly raise prices
      if (profit > 0 && loadFactor > 0.6) {
        multiplier += PRICE_ADJUSTMENT_STEP * 0.5
      }
    }
    
    // Personality adjustments
    personality match {
      case BotPersonality.AGGRESSIVE =>
        // Aggressive prefers market share over margin
        if (loadFactor < 0.7) multiplier -= PRICE_ADJUSTMENT_STEP
      case BotPersonality.BUDGET =>
        // Budget always tries to undercut
        if (priceRatioToCompetitors > 0.85) multiplier -= PRICE_ADJUSTMENT_STEP
      case BotPersonality.PREMIUM =>
        // Premium maintains high prices even if load factor suffers
        if (multiplier < 1.0) multiplier = Math.max(multiplier, 0.98)
      case _ =>
    }
    
    // Apply multiplier with bounds
    val finalMultiplier = Math.max(MIN_PRICE_MULTIPLIER, Math.min(MAX_PRICE_MULTIPLIER, multiplier))
    
    // Ensure we don't go below minimum viable prices
    val minPrice = (standardEconomy * MIN_PRICE_MULTIPLIER).toInt
    val maxPrice = (standardEconomy * MAX_PRICE_MULTIPLIER).toInt
    
    LinkClassValues.getInstance(
      Math.max(minPrice, Math.min(maxPrice, (currentEconomy * finalMultiplier).toInt)),
      Math.max((minPrice * 2), Math.min((maxPrice * 2), (currentBusiness * finalMultiplier).toInt)),
      Math.max((minPrice * 3), Math.min((maxPrice * 3), (currentFirst * finalMultiplier).toInt))
    )
  }
  
  /**
   * Respond to competition on shared routes - PHASE 3: Intelligent responses
   */
  private def respondToCompetition(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Analyzing competition (${personality})")
    
    val ownLinks = LinkSource.loadFlightLinksByAirlineId(airline.id)
    val consumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, 1)
    val consumptionByLink = consumptions.groupBy(_.link.id)
    
    var competitiveResponses = 0
    
    ownLinks.foreach { link =>
      // Find competing airlines on same route
      val allLinksOnRoute = (LinkSource.loadFlightLinksByAirports(link.from.id, link.to.id) ++
                            LinkSource.loadFlightLinksByAirports(link.to.id, link.from.id))
                            .filter(_.airline.id != airline.id)
      
      if (allLinksOnRoute.nonEmpty) {
        val competitorCount = allLinksOnRoute.size
        
        // Analyze competitor capacity and pricing
        val totalCompetitorCapacity = allLinksOnRoute.map(l => l.capacity.total * l.frequency).sum
        val ourCapacity = link.capacity.total * link.frequency
        val marketShare = if ((totalCompetitorCapacity + ourCapacity) > 0) {
          ourCapacity.toDouble / (totalCompetitorCapacity + ourCapacity)
        } else 0.0
        
        val avgCompetitorPrice = allLinksOnRoute.map(_.price(ECONOMY)).sum / competitorCount
        val ourPrice = link.price(ECONOMY)
        
        // Check if we're losing market share
        val consumption = consumptionByLink.get(link.id).flatMap(_.headOption)
        val loadFactor = consumption.map { c =>
          val cap = link.capacity.total * link.frequency
          if (cap > 0) c.link.soldSeats.total.toDouble / cap else 0.0
        }.getOrElse(0.5)
        
        // Determine competitive response based on personality
        val shouldRespond = (loadFactor < personality.targetCapacityLow) || 
                           (marketShare < 0.3 && competitorCount > 1) ||
                           (ourPrice > avgCompetitorPrice * 1.3 && personality != BotPersonality.PREMIUM)
        
        if (shouldRespond) {
          personality match {
            case BotPersonality.AGGRESSIVE =>
              // Aggressive: Match or undercut competitor prices
              if (ourPrice > avgCompetitorPrice) {
                val newPrice = (avgCompetitorPrice * 0.95).toInt
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.95).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    ⚔️  AGGRESSIVE: Cut prices on ${link.from.iata}->${link.to.iata} to undercut competition")
                competitiveResponses += 1
              }
              
            case BotPersonality.BUDGET =>
              // Budget: Always try to be cheapest
              val lowestCompetitor = allLinksOnRoute.map(_.price(ECONOMY)).min
              if (ourPrice >= lowestCompetitor) {
                val newPrice = Math.max((lowestCompetitor * 0.90).toInt, 
                                       (Pricing.computeStandardPrice(link.distance, 
                                         Computation.getFlightCategory(link.from, link.to), 
                                         ECONOMY, PassengerType.TRAVELER, link.from.baseIncome) * MIN_PRICE_MULTIPLIER).toInt)
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.90).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    💸 BUDGET: Slashed prices on ${link.from.iata}->${link.to.iata} to be cheapest")
                competitiveResponses += 1
              }
              
            case BotPersonality.PREMIUM =>
              // Premium: Ignore budget competitors, focus on quality
              val premiumCompetitors = allLinksOnRoute.filter(_.rawQuality >= 50)
              if (premiumCompetitors.isEmpty) {
                println(s"    💎 PREMIUM: No premium competitors on ${link.from.iata}->${link.to.iata} - maintaining position")
              }
              
            case BotPersonality.CONSERVATIVE =>
              // Conservative: Slight price adjustment, focus on stability
              if (loadFactor < 0.5) {
                val newPrice = Math.max((ourPrice * 0.95).toInt, 
                                       (Pricing.computeStandardPrice(link.distance,
                                         Computation.getFlightCategory(link.from, link.to),
                                         ECONOMY, PassengerType.TRAVELER, link.from.baseIncome) * 0.85).toInt)
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.97).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    🏛️  CONSERVATIVE: Moderate price cut on ${link.from.iata}->${link.to.iata}")
                competitiveResponses += 1
              }
              
            case BotPersonality.REGIONAL =>
              // Regional: Find niche, avoid head-on competition with big carriers
              if (marketShare < 0.2 && competitorCount > 2) {
                println(s"    🏔️  REGIONAL: Too much competition on ${link.from.iata}->${link.to.iata} - may abandon")
              }
              
            case BotPersonality.BALANCED =>
              // Balanced: Tactical response based on situation
              if (loadFactor < 0.6 && ourPrice > avgCompetitorPrice) {
                val newPrice = ((ourPrice + avgCompetitorPrice) / 2).toInt
                val newPricing = LinkClassValues.getInstance(
                  newPrice,
                  (link.price(BUSINESS) * 0.97).toInt,
                  link.price(FIRST)
                )
                val updatedLink = link.copy(price = newPricing)
                updatedLink.setAssignedAirplanes(link.getAssignedAirplanes())
                LinkSource.updateLink(updatedLink)
                println(s"    ⚖️  BALANCED: Adjusted prices on ${link.from.iata}->${link.to.iata} to match market")
                competitiveResponses += 1
              }
          }
        }
      }
    }
    
    if (competitiveResponses > 0) {
      println(s"[${airline.name}] Made $competitiveResponses competitive responses")
    } else {
      println(s"[${airline.name}] No competitive action needed")
    }
  }
  
  /**
   * Evaluate and abandon unprofitable routes
   */
  private def evaluateRouteAbandonment(airline: Airline, personality: BotPersonality, cycle: Int): Unit = {
    println(s"[${airline.name}] Evaluating route profitability...")
    
    val links = LinkSource.loadFlightLinksByAirlineId(airline.id)
    if (links.isEmpty) return
    
    // Load multiple cycles of consumption data to detect trends
    val linkConsumptions = LinkSource.loadLinkConsumptionsByAirline(airline.id, UNPROFITABLE_CYCLES_THRESHOLD)
    val consumptionByLink = linkConsumptions.groupBy(_.link.id)
    
    var abandondedRoutes = 0
    
    links.foreach { link =>
      val consumptionHistory = consumptionByLink.getOrElse(link.id, List.empty)
      
      if (consumptionHistory.size >= UNPROFITABLE_CYCLES_THRESHOLD) {
        // Check for consistent losses
        val unprofitableCycles = consumptionHistory.count(_.profit < 0)
        val avgLoadFactor = {
          val lfs = consumptionHistory.map { c =>
            val cap = c.link.capacity.total * c.link.frequency
            if (cap > 0) c.link.soldSeats.total.toDouble / cap else 0.0
          }
          if (lfs.nonEmpty) lfs.sum / lfs.size else 0.0
        }
        
        // Decide if we should abandon this route
        val shouldAbandon = (unprofitableCycles >= UNPROFITABLE_CYCLES_THRESHOLD) ||
                           (avgLoadFactor < MIN_LOAD_FACTOR_THRESHOLD && consumptionHistory.size >= 3)
        
        // Personality affects abandonment decision
        val personalityAllowsAbandonment = personality match {
          case BotPersonality.AGGRESSIVE => avgLoadFactor < 0.2 // Very reluctant to abandon
          case BotPersonality.CONSERVATIVE => avgLoadFactor < 0.4 // Quick to cut losses
          case BotPersonality.BUDGET => avgLoadFactor < 0.35 // Needs good utilization
          case _ => avgLoadFactor < MIN_LOAD_FACTOR_THRESHOLD
        }
        
        if (shouldAbandon && personalityAllowsAbandonment) {
          // Remove the link
          LinkSource.deleteLink(link.id)
          println(s"    ❌ ABANDONED: ${link.from.iata}->${link.to.iata} (${unprofitableCycles} unprofitable cycles, ${(avgLoadFactor*100).toInt}% avg LF)")
          abandondedRoutes += 1
        }
      }
    }
    
    if (abandondedRoutes > 0) {
      println(s"[${airline.name}] Abandoned $abandondedRoutes unprofitable routes")
    } else {
      println(s"[${airline.name}] All routes performing acceptably")
    }
  }
}

/**
 * Bot personality types with different strategies
 * PHASE 3: Added priceMultiplier for dynamic pricing
 */
sealed trait BotPersonality {
  def minAirportSize: Int
  def minPopulation: Long
  def targetCapacityLow: Double
  def targetCapacityHigh: Double
  def fleetBudgetRatio: Double
  def serviceQuality: Double
  def priceMultiplier: Double // Base price multiplier for this personality
  
  def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double
  def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String
  def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double]
  def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int]
  def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int
}

object BotPersonality {
  
  case object AGGRESSIVE extends BotPersonality {
    val minAirportSize = 3
    val minPopulation = 500000L
    val targetCapacityLow = 0.60
    val targetCapacityHigh = 0.90
    val fleetBudgetRatio = 0.25 // Spend 25% on fleet
    val serviceQuality = 50.0 // Moderate service
    val priceMultiplier = 0.92 // Slightly below market - competitive pricing
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer larger airports, longer routes
      val sizeScore = airport.size * 15.0
      val popScore = Math.log10(airport.population) * 10.0
      val distanceScore = if (distance > 3000) 20.0 else distance / 150.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (currentFleet.size < 10) "REGIONAL" else "LARGE"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Competitive pricing - 10% below standard
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.90),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.90),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.90)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Growth-focused: 80% economy, 15% business, 5% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.80).toInt,
        BUSINESS -> (totalSeats * 0.15).toInt,
        FIRST -> (totalSeats * 0.05).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // High frequency for competitive edge
      val baseFrequency = (distance / 500.0).toInt + 2
      Math.min(baseFrequency, 14) // Cap at 14 weekly flights (2 per day)
    }
  }
  
  case object CONSERVATIVE extends BotPersonality {
    val minAirportSize = 5
    val minPopulation = 2000000L
    val targetCapacityLow = 0.75
    val targetCapacityHigh = 0.95
    val fleetBudgetRatio = 0.15
    val serviceQuality = 65.0 // Good service
    val priceMultiplier = 1.12 // Above market - premium for reliability
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer major hubs, established routes
      val sizeScore = airport.size * 25.0
      val popScore = Math.log10(airport.population) * 15.0
      val distanceScore = if (distance < 5000 && distance > 1000) 15.0 else 5.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (avgAge > 15) "MEDIUM" else "LARGE" // Replace aging fleet first
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Premium pricing - 15% above standard
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 1.15),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 1.15),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 1.15)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Traditional: 70% economy, 20% business, 10% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.70).toInt,
        BUSINESS -> (totalSeats * 0.20).toInt,
        FIRST -> (totalSeats * 0.10).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Steady frequency
      val baseFrequency = (distance / 700.0).toInt + 1
      Math.min(baseFrequency, 10) // Cap at 10 weekly flights
    }
  }
  
  case object BALANCED extends BotPersonality {
    val minAirportSize = 4
    val minPopulation = 1000000L
    val targetCapacityLow = 0.70
    val targetCapacityHigh = 0.88
    val fleetBudgetRatio = 0.18
    val serviceQuality = 55.0 // Standard service
    val priceMultiplier = 1.0 // Market rate
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      val sizeScore = airport.size * 18.0
      val popScore = Math.log10(airport.population) * 12.0
      val distanceScore = distance / 200.0
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      if (currentFleet.size % 3 == 0) "LARGE" else "MEDIUM"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Market rate pricing
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome).toDouble,
        BUSINESS -> Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome).toDouble,
        FIRST -> Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome).toDouble
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Standard: 75% economy, 20% business, 5% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.75).toInt,
        BUSINESS -> (totalSeats * 0.20).toInt,
        FIRST -> (totalSeats * 0.05).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Moderate frequency
      val baseFrequency = (distance / 600.0).toInt + 1
      Math.min(baseFrequency, 12) // Cap at 12 weekly flights
    }
  }
  
  case object REGIONAL extends BotPersonality {
    val minAirportSize = 2
    val minPopulation = 100000L
    val targetCapacityLow = 0.65
    val targetCapacityHigh = 0.85
    val fleetBudgetRatio = 0.20
    val serviceQuality = 45.0 // Basic service
    val priceMultiplier = 0.95 // Slightly below market
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer smaller airports, shorter routes
      val sizeScore = if (airport.size <= 4) airport.size * 25.0 else airport.size * 5.0
      val popScore = Math.log10(airport.population) * 8.0
      val distanceScore = if (distance < 2000) 25.0 else 2000.0 / distance
      val sameCountry = if (airport.countryCode == fromAirport.countryCode) 30.0 else 0.0
      sizeScore + popScore + distanceScore + sameCountry
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      "REGIONAL"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Regional pricing - slightly below market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.95),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.95),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.95)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Efficient: 85% economy, 15% business, no first class
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.85).toInt,
        BUSINESS -> (totalSeats * 0.15).toInt,
        FIRST -> 0
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // High frequency on short routes
      val baseFrequency = if (distance < 1000) {
        (1000.0 / distance).toInt + 2
      } else {
        (distance / 800.0).toInt + 1
      }
      Math.min(baseFrequency, 21) // Cap at 21 weekly flights (3 per day)
    }
  }
  
  case object PREMIUM extends BotPersonality {
    val minAirportSize = 6
    val minPopulation = 5000000L
    val targetCapacityLow = 0.70
    val targetCapacityHigh = 0.85 // Lower utilization OK for premium
    val fleetBudgetRatio = 0.22
    val serviceQuality = 80.0 // Excellent service
    val priceMultiplier = 1.35 // Premium pricing - well above market
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer major hubs, long-haul premium routes
      val sizeScore = airport.size * 30.0
      val popScore = Math.log10(airport.population) * 18.0
      val distanceScore = if (distance > 5000) 40.0 else distance / 125.0
      val income = if (airport.incomeLevel >= 50) 25.0 else 0.0
      sizeScore + popScore + distanceScore + income
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      // Always prefer large aircraft for premium service
      "LARGE"
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Premium pricing - significantly above market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 1.20),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 1.40),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 1.60)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // Luxury mix: 50% economy, 30% business, 20% first
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> (totalSeats * 0.50).toInt,
        BUSINESS -> (totalSeats * 0.30).toInt,
        FIRST -> (totalSeats * 0.20).toInt
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Moderate frequency - focus on quality over quantity
      val baseFrequency = (distance / 800.0).toInt + 1
      Math.min(baseFrequency, 7) // Cap at 7 weekly flights (1 per day)
    }
  }
  
  case object BUDGET extends BotPersonality {
    val minAirportSize = 4
    val minPopulation = 1000000L
    val targetCapacityLow = 0.80 // High utilization required
    val targetCapacityHigh = 0.98
    val fleetBudgetRatio = 0.12 // Low fleet spending
    val serviceQuality = 30.0 // Basic service
    val priceMultiplier = 0.72 // Deep discount pricing
    
    def scoreDestination(airport: Airport, distance: Int, fromAirport: Airport): Double = {
      // Prefer high-demand, short-haul routes
      val sizeScore = airport.size * 20.0
      val popScore = Math.log10(airport.population) * 15.0
      val distanceScore = if (distance < 3000) 30.0 else 3000.0 / distance
      sizeScore + popScore + distanceScore
    }
    
    def preferredAircraftCategory(currentFleet: List[Airplane], avgAge: Int): String = {
      "SMALL" // Efficient single-aisle aircraft
    }
    
    def calculatePricing(fromAirport: Airport, toAirport: Airport, distance: Int): Map[LinkClass, Double] = {
      // Budget pricing - significantly below market
      val flightCategory = Computation.getFlightCategory(fromAirport, toAirport)
      val baseIncome = fromAirport.baseIncome
      Map(
        ECONOMY -> (Pricing.computeStandardPrice(distance, flightCategory, ECONOMY, PassengerType.TRAVELER, baseIncome) * 0.70),
        BUSINESS -> (Pricing.computeStandardPrice(distance, flightCategory, BUSINESS, PassengerType.TRAVELER, baseIncome) * 0.80),
        FIRST -> (Pricing.computeStandardPrice(distance, flightCategory, FIRST, PassengerType.TRAVELER, baseIncome) * 0.80)
      )
    }
    
    def configureLinkClasses(airplane: Airplane): Map[LinkClass, Int] = {
      // All economy: 100% economy, no business or first class
      val totalSeats = airplane.model.capacity
      Map(
        ECONOMY -> totalSeats,
        BUSINESS -> 0,
        FIRST -> 0
      )
    }
    
    def calculateOptimalFrequency(distance: Int, airplane: Airplane): Int = {
      // Very high frequency - maximize utilization
      val baseFrequency = (distance / 400.0).toInt + 3
      Math.min(baseFrequency, 21) // Cap at 21 weekly flights (3 per day)
    }
  }
}
