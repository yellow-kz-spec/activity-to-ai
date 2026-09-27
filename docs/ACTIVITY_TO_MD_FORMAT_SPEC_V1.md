# Activity to MD — Format Specification v1.0

## 1. Purpose

Activity to MD converts activity data from fitness platforms into a normalized, AI-ready Markdown representation.

The exported Markdown must prioritize:

- preservation of useful workout data;
- consistent structure across data sources;
- clear measurement units;
- AI readability;
- minimal source-specific technical noise;
- explicit handling of missing data;
- privacy by default;
- future extensibility to additional activity providers.

Garmin, Strava, and future providers are treated as **data adapters**. They must not define separate Markdown formats.

Architecture:

```text
Garmin Adapter ──┐
                 │
Strava Adapter ──┼──> Canonical Activity Model ──> Markdown Renderer
                 │
Future Adapter ──┘
```

## 2. Core principles

### 2.1 One canonical model

All providers map available fields into one internal Activity model. Equivalent measurements from different providers must use the same canonical field.

Example:

```text
Garmin directHeartRate
Strava heartrate
        ↓
heart_rate_bpm
```

Provider-specific values that do not have a true equivalent must remain separate. For example, Garmin Training Load and Strava Relative Effort must never be treated as interchangeable metrics.

### 2.2 Preserve data, do not invent data

Activity to MD must:

- export measurements provided by the source;
- normalize units where appropriate;
- omit unavailable values;
- never replace missing values with zero;
- never estimate a missing value unless explicitly defined as a derived Activity to MD field;
- identify source-derived or estimated metrics when possible.

### 2.3 Dynamic sections

Sections must only be generated when useful data exists. For example, no power means omit `Power`; no running dynamics means omit `Running Dynamics`; no segments means omit `Segments`; no weather means omit `Weather`.

Never generate tables containing only empty cells or `null`.

## 3. Canonical activity types

Initial v1 canonical types:

```text
running
cycling
walking
hiking
swimming
other
```

Provider-specific types map into these canonical values. The original source activity type may optionally be retained internally for debugging but does not need to appear in Markdown if it adds no useful information.

## 4. Markdown section order

Sections should always appear in this order when applicable:

```text
# Activity title

## Metadata
## Summary
## Training Metrics
## Running Dynamics / Cycling Dynamics / Sport-specific Dynamics
## Power
## Weather
## Heart Rate Zones
## Power Zones
## Laps
## Splits
## Segments
## Time Series
## Data Availability
## Source Notes
## Instruction for AI
```

Sections with no data are omitted.

## 5. Metadata

| Field | Type | Required | Notes |
|---|---|---:|---|
| source | string | yes | Garmin, Strava, etc. |
| source_activity_id | string | yes | Provider activity ID |
| activity_type | enum | yes | Canonical type |
| title | string | no | Source activity name |
| start_time | datetime | yes | ISO 8601 with actual UTC offset |
| timezone | string | no | IANA timezone where available |
| device | string | no | Recording device |
| gear | string | no | Shoes, bike, etc. |
| location | string | no | Human-readable location |
| coordinates_included | boolean | yes | Privacy state |

Preferred time representation:

```text
2026-08-25T07:32:34-04:00
```

Do not append `Z` to local time. `Z` may only be used for actual UTC timestamps.

## 6. Summary

The Summary section contains broadly applicable workout metrics.

| Canonical field | Unit |
|---|---|
| distance | km |
| duration | hh:mm:ss |
| moving_duration | hh:mm:ss |
| elapsed_duration | hh:mm:ss |
| average_pace | min/km |
| average_speed | m/s |
| maximum_speed | m/s |
| elevation_gain | m |
| elevation_loss | m |
| calories | kcal |
| average_heart_rate | bpm |
| maximum_heart_rate | bpm |

For running, prefer pace as the primary human-readable speed metric. For cycling, prefer speed. Raw speed in m/s may still be retained where useful for time-series consistency.

## 7. Training Metrics

Provider-derived training metrics are kept here.

| Metric | Value | Source |
|---|---:|---|
| Aerobic Training Effect | ... | Garmin |
| Anaerobic Training Effect | ... | Garmin |
| Training Load | ... | Garmin |
| Starting Stamina | ... | Garmin |
| Ending Stamina | ... | Garmin |
| Relative Effort | ... | Strava |

Do not mathematically convert one provider's proprietary metric into another.

## 8. Running Dynamics

Applicable to `running` activities.

| Field | Unit |
|---|---|
| average_cadence | spm |
| maximum_cadence | spm |
| stride_length | cm |
| ground_contact_time | ms |
| ground_contact_balance_left | % |
| vertical_oscillation | cm |
| vertical_ratio | % |
| average_respiration | breaths/min |

Additional metrics may be added later without breaking v1 readers.

## 9. Cadence normalization

Running cadence must use `steps per minute (spm)`.

Garmin running cadence can be used directly when already expressed as full steps per minute. Strava running cadence values representing complete gait cycles / one-side cadence must be normalized:

```text
canonical_spm = strava_run_cadence × 2
```

This conversion applies only where provider semantics are known. Do not automatically double cycling cadence. Cycling cadence remains `rpm`.

## 10. Power

| Field | Unit |
|---|---|
| average_power | W |
| maximum_power | W |
| normalized_power | W |
| weighted_average_power | W |
| work | kJ |

`Normalized Power` and `Weighted Average Power` must remain separate concepts if the source exposes them under different algorithms. Do not rename one into the other merely to make sources appear identical.

When only one exists, export only that one. Estimated power must be labeled as estimated when the source semantics indicate it is estimated.

## 11. Weather

| Field | Unit |
|---|---|
| condition | string |
| temperature | °F or °C |
| feels_like | °F or °C |
| dew_point | °F or °C |
| humidity | % |
| wind_direction | degrees/cardinal |
| wind_speed | mph or km/h |
| wind_gust | mph or km/h |
| precipitation | in or mm |
| weather_station | string |
| weather_source | string |

Weather source must always be identified, for example `Garmin` or `Open-Meteo historical`. Activity to MD must not imply that Open-Meteo weather originated from Strava.

## 12. Heart Rate Zones

Canonical structure:

```markdown
## Heart Rate Zones

| Zone | Time |
|---:|---:|
| 1 | 03:10 |
| 2 | 25:33 |
| 3 | 09:11 |
| 4 | 00:28 |
| 5 | 00:00 |
```

Use human-readable duration rather than decimal seconds in final Markdown. Internally durations may remain numeric seconds. Do not generate zones if the provider does not supply reliable zone data.

## 13. Power Zones

Use the same structure as Heart Rate Zones. Do not infer power zones from power data unless future product requirements explicitly define such behavior.

## 14. Laps

All providers map laps into the same table.

Canonical lap fields include:

- lap;
- distance;
- duration;
- moving_duration;
- pace/speed;
- average_heart_rate;
- maximum_heart_rate;
- average_power;
- maximum_power;
- cadence;
- elevation_gain.

Only available columns should be rendered.

Provider API objects must not be dumped directly into Markdown. Exclude technical fields such as `resource_state`, `id_str`, `athlete`, `visibility`, `start_index`, `end_index`, and `hidden` unless they later serve an explicit product feature.

## 15. Splits

Splits and laps are not assumed to be identical.

Canonical fields include:

- split;
- distance;
- elapsed_time;
- moving_time;
- pace;
- average_speed;
- grade_adjusted_pace/speed;
- average_heart_rate;
- elevation_change.

## 16. Segments

Segments are source-specific data, currently primarily Strava. They remain useful and should be preserved.

Canonical segment fields include:

- name;
- distance;
- elapsed_time;
- moving_time;
- pace/speed;
- average_heart_rate;
- maximum_heart_rate;
- average_power;
- average_grade;
- maximum_grade;
- achievement / PR information when useful.

Do not include full nested provider objects. Do not export segment GPS coordinates when GPS privacy is disabled.

## 17. Time Series

Time Series is a first-class component of Activity to MD and should be preserved because it enables deeper AI analysis than summary metrics alone.

### 17.1 Canonical general fields

```text
elapsed_s
distance_m
heart_rate_bpm
speed_mps
elevation_m
power_w
cadence
grade_pct
moving
```

### 17.2 Running-specific fields

Where available:

```text
cadence_spm
grade_adjusted_speed_mps
stamina_pct
potential_stamina_pct
stride_length_cm
vertical_oscillation_cm
vertical_ratio_pct
ground_contact_time_ms
ground_contact_balance_left_pct
respiration_bpm
performance_condition
```

### 17.3 Provider mapping examples

```text
Garmin directHeartRate     → heart_rate_bpm
Strava heartrate           → heart_rate_bpm
Garmin sumDistance         → distance_m
Strava distance            → distance_m
Garmin directSpeed         → speed_mps
Strava velocity_smooth     → speed_mps
Garmin directElevation     → elevation_m
Strava altitude            → elevation_m
Garmin directPower         → power_w
Strava watts               → power_w
```

Raw provider names must not appear in the final canonical table where a standard name exists.

## 18. Time-series sampling

The renderer may reduce time-series density to control file size. Sampling must preserve the overall temporal structure of the activity.

When sampling occurs, disclose it before the table:

```text
Sampling: reduced
Method: uniform
Approximate interval: 3 s
Original points: 2,210
Exported points: 737
```

Never silently sample data.

v1 should retain a practical maximum of approximately 1,000 time-series rows. This is a presentation limit, not a canonical model limit. The internal model may retain all received points.

## 19. Precision

Raw API floating-point noise must not appear unnecessarily in final Markdown.

Recommended display precision:

| Metric | Precision |
|---|---:|
| distance km | 2 decimals |
| distance m | 1 decimal |
| heart rate | integer |
| power | integer |
| speed | 2 decimals |
| elevation | 1 decimal |
| percentages | 1–2 decimals |
| cadence | 1 decimal |
| time | human-readable |
| temperature | 1 decimal |

Do not modify the underlying canonical value solely for presentation.

## 20. GPS privacy

Activity to MD v1 excludes exact GPS coordinates by default.

This includes:

- start coordinates;
- end coordinates;
- lat/lng time-series columns;
- segment coordinates;
- polylines;
- maps.

Human-readable location may remain where available.

A future setting may introduce `Include GPS coordinates: On / Off`, with the default `Off`.

The privacy rule must apply equally to Garmin and Strava.

## 21. Data Availability

Every file should end with a human- and machine-friendly indication of important available or intentionally excluded data.

Example:

```markdown
## Data Availability

- Heart rate: available
- Power: available
- Running dynamics: available
- Heart-rate zones: available
- Training Effect: available from Garmin
- Relative Effort: unavailable from this source
- GPS coordinates: excluded by privacy policy
```

`unavailable` means the data was not supplied. `excluded` means Activity to MD intentionally removed it.

## 22. Source Notes

Optional source-specific caveats may be included, for example:

```markdown
## Source Notes

- Relative Effort is a Strava-derived metric.
- Training Effect is a Garmin-derived metric.
- Weather was retrieved from Open-Meteo using activity time and approximate location.
- Missing values were not replaced with zero.
```

Do not clutter this section with implementation details.

## 23. Instruction for AI

Every Activity to MD file ends with:

```markdown
## Instruction for AI

Analyze the workout using the supplied measurements.

Distinguish:
- directly observed measurements;
- provider-derived metrics;
- Activity to MD normalized values;
- inference.

Do not interpret missing values as zero.

Explicitly note missing context when it materially affects the analysis.

Prefer trends and relationships between measurements over isolated values when time-series data is available.
```

## 24. Provider precedence

Activity to MD does not currently merge Garmin and Strava records of the same workout. Each exported file represents one source activity.

Therefore there is no field-level source precedence in v1. If multi-source merging is added later, it must be specified separately rather than silently selecting one provider's value.

## 25. Canonical model outline

```text
Activity
├── metadata
├── summary
├── training_metrics
├── sport_dynamics
├── power
├── weather
├── heart_rate_zones
├── power_zones
├── laps[]
├── splits[]
├── segments[]
├── time_series[]
├── data_availability
└── source_notes
```

Each provider adapter populates as much of this model as it can. The Markdown renderer must not contain Garmin-specific or Strava-specific parsing logic.

## 26. Responsibilities

### Garmin Adapter

Responsible for:

- parsing Garmin source data;
- mapping Garmin names into canonical fields;
- preserving Garmin-only metrics;
- providing Garmin weather when available;
- mapping Garmin running dynamics;
- mapping zones;
- mapping stamina and Training Effect;
- mapping time series.

It must not generate Markdown directly.

### Strava Adapter

Responsible for:

- parsing Strava API responses;
- mapping Strava names into canonical fields;
- normalizing running cadence;
- preserving Relative Effort;
- preserving segment data;
- mapping streams;
- exposing whether power is device-measured or source-estimated when known.

It must not generate raw API JSON into Markdown and must not generate Markdown directly.

### Markdown Renderer

Responsible for:

- section ordering;
- formatting;
- units;
- precision;
- omission of empty sections;
- time formatting;
- enforcing export privacy;
- applying file-size-aware time-series presentation.

## 27. Compatibility rule

Future additions to the canonical model are allowed. A new field must not require changing the meaning of an existing v1 field.

New sport-specific sections such as `Cycling Dynamics`, `Swimming Dynamics`, and `Skiing Dynamics` may be added without breaking the overall file structure.

## 28. Acceptance criteria for v1

Activity to MD v1 is considered implemented when:

1. Garmin running and Strava running generate the same section structure for equivalent data.
2. Garmin cycling and Strava cycling generate the same section structure for equivalent data.
3. Provider-specific useful metrics remain present.
4. Strava laps, splits, and segments are no longer raw JSON dumps.
5. Running cadence uses consistent `spm`.
6. Missing fields are omitted rather than rendered as `null` or zero.
7. Exact GPS coordinates are excluded consistently by default.
8. Local start times contain a correct timezone offset.
9. Time-series columns use canonical names.
10. Floating-point noise is removed from presentation.
11. Weather source is explicitly stated.
12. Time-series sampling is disclosed.
13. Garmin- and Strava-specific parsing is isolated from the Markdown renderer.
14. Existing useful Garmin metrics are not lost during the refactor.
15. Existing useful Strava metrics are not lost during the refactor.

## 29. Reference output philosophy

The final Markdown should read like a structured workout record, not an API dump.

A human should be able to understand it. An AI should be able to analyze it without first reverse-engineering Garmin or Strava terminology.

The source should determine **what information is available**.

Activity to MD should determine **how that information is represented**.
