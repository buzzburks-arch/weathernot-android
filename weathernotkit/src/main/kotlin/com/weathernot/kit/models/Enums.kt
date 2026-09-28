package com.weathernot.kit.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** An enum as the API spells it (`"track_meet"`, `"no_go"`). */
interface WireEnum {
    val wire: String
}

/**
 * Decodes a [WireEnum] by its wire name, and a value this app doesn't
 * know yet (the server gained a new one) as [fallback] rather than
 * failing the whole response.
 */
open class WireEnumSerializer<T>(
    name: String,
    private val values: Array<T>,
    private val fallback: T,
) : KSerializer<T> where T : Enum<T>, T : WireEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): T {
        val wire = decoder.decodeString()
        return values.firstOrNull { it.wire == wire } ?: fallback
    }
}

@Serializable(with = UnitSystem.Serializer::class)
enum class UnitSystem(override val wire: String) : WireEnum {
    IMPERIAL("imperial"),
    METRIC("metric");

    object Serializer : WireEnumSerializer<UnitSystem>("UnitSystem", entries.toTypedArray(), IMPERIAL)
}

@Serializable(with = EventType.Serializer::class)
enum class EventType(override val wire: String) : WireEnum {
    WEDDING("wedding"),
    PARTY("party"),
    REUNION("reunion"),
    PICNIC("picnic"),
    TAILGATE("tailgate"),
    FESTIVAL("festival"),
    CONCERT("concert"),
    FIREWORKS("fireworks"),
    FARMERS_MARKET("farmers_market"),
    YOUTH_SPORTS("youth_sports"),
    TRACK_MEET("track_meet"),
    RACE("race"),
    GOLF("golf"),
    HIKE("hike"),
    CAMP("camp"),
    PHOTO_SHOOT("photo_shoot"),
    CONSTRUCTION("construction"),
    OTHER("other");

    /** "track_meet" -> "Track meet", like the backend's `event_type_label`. */
    val label: String
        get() = wire.replace('_', ' ').replaceFirstChar { it.uppercase() }

    object Serializer : WireEnumSerializer<EventType>("EventType", entries.toTypedArray(), OTHER)
}

@Serializable(with = GridVariable.Serializer::class)
enum class GridVariable(override val wire: String, val label: String) : WireEnum {
    PRECIP_PROBABILITY("precip_probability", "Chance of rain"),
    PRECIP_AMOUNT("precip_amount", "Precipitation amount"),
    WIND_SPEED("wind_speed", "Wind speed"),
    WIND_GUST("wind_gust", "Wind gusts"),
    TEMP_2M("temp_2m", "Temperature"),
    HEAT_INDEX("heat_index", "Heat index"),
    LIGHTNING_PROBABILITY("lightning_probability", "Chance of lightning"),
    NWS_ALERT("nws_alert", "National Weather Service alerts"),
    WIND_CHILL("wind_chill", "Wind chill"),
    DEW_POINT("dew_point", "Dew point"),
    SNOWFALL("snowfall", "Snowfall per hour"),
    SMOKE("smoke", "Wildfire smoke"),
    DARKNESS("darkness", "Darkness during the event"),

    /** A variable added to the API after this build. Shown, never offered. */
    UNKNOWN("unknown", "Other weather");

    object Serializer : WireEnumSerializer<GridVariable>("GridVariable", entries.toTypedArray(), UNKNOWN)

    companion object {
        /** The ones a criterion can be made on, in the web app's picker order. */
        val pickable: List<GridVariable> = entries.filter { it != UNKNOWN }
    }
}

@Serializable(with = Operator.Serializer::class)
enum class Operator(override val wire: String) : WireEnum {
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte");

    val isUp: Boolean get() = this == GT || this == GTE

    object Serializer : WireEnumSerializer<Operator>("Operator", entries.toTypedArray(), GTE)
}

/** "watch": past a criterion's Watch (heads-up) level but not its No-go. */
@Serializable(with = GoNoGo.Serializer::class)
enum class GoNoGo(override val wire: String) : WireEnum {
    GO("go"),
    WATCH("watch"),
    NO_GO("no_go"),
    UNKNOWN("unknown");

    object Serializer : WireEnumSerializer<GoNoGo>("GoNoGo", entries.toTypedArray(), UNKNOWN)
}

/** Where an event is in its watch. */
@Serializable(with = WatchPhase.Serializer::class)
enum class WatchPhase(override val wire: String, val label: String) : WireEnum {
    OUTLOOK("outlook", "Historical outlook"),
    FORECAST("forecast", "Forecast"),
    LIVE("live", "Live"),
    ENDED("ended", "Ended");

    object Serializer : WireEnumSerializer<WatchPhase>("WatchPhase", entries.toTypedArray(), FORECAST)
}
