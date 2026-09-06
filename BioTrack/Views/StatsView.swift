import SwiftUI
// Access ChartSeries/MultiSeriesChart

// Forward declaration removed; using the implementation in Components/MultiSeriesChart.swift

struct StatsView: View {
    @EnvironmentObject var state: AppState
    @State private var selectedMetric: Metric?
    @State private var selectedMetricIds: Set<UUID> = []
    @State private var showTrackSheet = false
    enum Mode { case chart, calendar }
    @State private var mode: Mode = .chart
    enum Range { case d7, d30, d90, all }
    @State private var range: Range = .d30
    @State private var selectedProtocolId: UUID? = nil
    @State private var selectedSupplementId: UUID? = nil
    enum Grouping { case metrics, protocols, supplements }
    @State private var grouping: Grouping = .metrics
    enum ChartStyle { case line, bar }
    @State private var chartStyle: ChartStyle = .line
    @State private var showingExperimentWizard = false
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    selectMetricCard
                    timeAndFilters
                    mainPanel
                    correlationsPanel
                    experimentsPanel
                }
                .padding()
            }
            .navigationTitle("Statistiques")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: { showingExperimentWizard = true }) {
                        Label("N-of-1", systemImage: "flask")
                    }
                }
            }
        }
        .sheet(isPresented: $showTrackSheet) { TrackView().environmentObject(state).withSheetDetentsIfAvailable() }
        .sheet(isPresented: $showingExperimentWizard) {
            NOf1WizardSheet(metrics: state.metrics) { input in
                state.createExperiment(input: input)
            }
            .withSheetDetentsIfAvailable()
        }
        .onAppear {
            if !applyScreenshotMetricSelectionIfRequested(), selectedMetric == nil {
                selectedMetric = metricsForSelection().first(where: { hasData(for: $0) }) ?? metricsForSelection().first
            }
            if selectedMetricIds.isEmpty, let first = selectedMetric { selectedMetricIds = [first.id] }
            state.refreshInsightsAndRecommendations()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Visualisez l'évolution de vos données")
                .foregroundColor(.secondary)
            Picker("", selection: $mode) {
                Label("Graphique", systemImage: "chart.bar").tag(Mode.chart)
                Label("Calendrier", systemImage: "calendar").tag(Mode.calendar)
            }
            .pickerStyle(.segmented)
            .accessibilityLabel("Mode d’affichage")
        }
    }

    private var selectMetricCard: some View {
        SurfaceCard {
            Text("Sélectionner une métrique").font(.headline)
            if metricsForSelection().isEmpty {
                VStack(spacing: 12) {
                    Text("Aucune métrique disponible").foregroundColor(.secondary)
                    Button(action: { showTrackSheet = true }) { Label("Ajouter des données", systemImage: "plus") }
                        .buttonStyle(.borderedProminent).tint(Color("Primary"))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(metricsForSelection()) { m in
                            let isSelected = selectedMetricIds.contains(m.id)
                            let label = hasData(for: m) ? m.name : "\(m.name) · vide"
                            SelectableChip(
                                title: label,
                                selected: isSelected,
                                iconSystemName: nil,
                                tintColor: nil,
                                selectedBackgroundColor: Color(UIColor.secondarySystemBackground),
                                selectedForegroundColor: Color("Primary")
                            ) {
                                if isSelected {
                                    if selectedMetricIds.count > 1 {
                                        selectedMetricIds.remove(m.id)
                                        if selectedMetric?.id == m.id {
                                            selectedMetric = metricsForSelection().first {
                                                selectedMetricIds.contains($0.id)
                                            }
                                        }
                                    }
                                } else if selectedMetricIds.count < 3 {
                                    selectedMetricIds.insert(m.id)
                                    selectedMetric = m
                                }
                            }
                            .opacity(isSelected || selectedMetricIds.count < 3 ? 1.0 : 0.5)
                            .disabled(!isSelected && selectedMetricIds.count >= 3)
                        }
                    }
                }
                Text("Les métriques marquées “vide” n'ont pas encore de donnée enregistrée.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }

    private var timeAndFilters: some View {
        SurfaceCard {
            Text("Période").font(.headline)
            HStack(spacing: 8) {
                SelectableChip(title: "7j", selected: range == .d7) { range = .d7 }
                SelectableChip(title: "30j", selected: range == .d30) { range = .d30 }
                SelectableChip(title: "90j", selected: range == .d90) { range = .d90 }
                SelectableChip(title: "Tout", selected: range == .all) { range = .all }
            }
            Text("Données affichées").font(.headline)
            Picker("Données affichées", selection: $grouping) {
                Text("Métriques").tag(Grouping.metrics)
                Text("Protocoles").tag(Grouping.protocols)
                Text("Suppléments").tag(Grouping.supplements)
            }
            .pickerStyle(.segmented)

            if grouping == .protocols {
                Text("Filtrer les protocoles").font(.subheadline.weight(.semibold))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SelectableChip(title: "Tous", selected: selectedProtocolId == nil) { selectedProtocolId = nil }
                        ForEach(state.protocols) { protocolItem in
                            SelectableChip(
                                title: protocolItem.name,
                                selected: selectedProtocolId == protocolItem.id
                            ) {
                                selectedProtocolId = selectedProtocolId == protocolItem.id ? nil : protocolItem.id
                            }
                        }
                    }
                }
            }

            if grouping == .supplements {
                Text("Filtrer les suppléments").font(.subheadline.weight(.semibold))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        SelectableChip(title: "Tous", selected: selectedSupplementId == nil) { selectedSupplementId = nil }
                        ForEach(state.supplements) { supplement in
                            SelectableChip(
                                title: supplement.name,
                                selected: selectedSupplementId == supplement.id
                            ) {
                                selectedSupplementId = selectedSupplementId == supplement.id ? nil : supplement.id
                            }
                        }
                    }
                }
            }
        }
    }

    private var mainPanel: some View {
        Group {
            if let m = selectedMetric {
                if mode == .chart {
                    SurfaceCard {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(chartSectionTitle)
                                    .font(.headline)
                                Text(periodDescription)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Picker("Type de graphique", selection: $chartStyle) {
                                Image(systemName: "waveform.path.ecg").tag(ChartStyle.line)
                                Image(systemName: "chart.bar").tag(ChartStyle.bar)
                            }
                            .pickerStyle(.segmented)
                            .frame(maxWidth: 120)
                        }
                        let metricsList = metricsListForDisplay(baseMetric: m)
                        let primaryMetric = metricsList.first ?? m
                        let isStandardizedComparison = shouldNormalizeMetricsComparison(metricsList: metricsList)
                        let series = buildSeries(from: metricsList, normalizeMetrics: isStandardizedComparison)
                        let forcedRange = isStandardizedComparison
                        ? (-3.0 as Double?, 3.0 as Double?)
                        : forcedYRange(for: primaryMetric, series: series)

                        if series.allSatisfy({ $0.points.isEmpty }) {
                            EmptyStateView(
                                text: "Aucune donnée sur cette période avec les filtres sélectionnés.",
                                systemImageName: "chart.line.uptrend.xyaxis"
                            )
                            .frame(maxWidth: .infinity, minHeight: 220)
                        } else {
                            VStack(spacing: 8) {
                                if isStandardizedComparison {
                                    Label(
                                        "Unités différentes : chaque série est centrée sur sa médiane et affichée en écarts standardisés, limités visuellement à ±3. Les valeurs réelles restent accessibles au toucher.",
                                        systemImage: "equal.circle"
                                    )
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                                } else if grouping != .metrics {
                                    Label(
                                        "Les jours planifiés sans réalisation apparaissent à zéro pour refléter la régularité réelle.",
                                        systemImage: "calendar.badge.clock"
                                    )
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                                }
                                legendRow(series: series)
                                MultiSeriesChart(
                                    series: series,
                                    style: chartStyle,
                                    unit: isStandardizedComparison ? "Écart standardisé" : unitLabel(baseMetric: primaryMetric),
                                    yAxisMode: yAxisMode(for: primaryMetric, isNormalizedComparison: isStandardizedComparison),
                                    ticks: customTicks(),
                                    yMinForced: forcedRange.0,
                                    yMaxForced: forcedRange.1,
                                    valueFormatter: isStandardizedComparison
                                        ? { String(format: "%.1f", $0) }
                                        : valueFormatterForMetric(primaryMetric),
                                    avgLineValue: isStandardizedComparison || series.count > 1
                                        ? nil
                                        : averageValue(for: primaryMetric, series: series),
                                    showLegend: false,
                                    showTemporalGaps: grouping == .metrics
                                )
                                .frame(height: 300)
                                .padding(.vertical, 6)
                                .padding(.bottom, 12)
                                if grouping == .metrics && seriesContainTemporalGaps(series) {
                                    Label(
                                        "Les segments pointillés indiquent une ou plusieurs journées sans mesure.",
                                        systemImage: "line.diagonal"
                                    )
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                averageRow(for: primaryMetric, series: series)
                            }
                            if grouping == .metrics {
                                HStack {
                                    Spacer()
                                    Button {
                                        let csv = ExportService.csvMetrics(
                                            metrics: state.metrics,
                                            entries: metricsList.flatMap { filteredEntries(for: $0) }
                                        )
                                        share(text: csv)
                                    } label: {
                                        Label("Exporter CSV", systemImage: "square.and.arrow.up")
                                    }
                                    .buttonStyle(.bordered)
                                }
                            }
                        }
                    }
                } else {
                    let (start, end) = dateBounds()
                    VStack(alignment: .leading, spacing: 12) {
                        SurfaceCard {
                            UnifiedCalendarView(days: rangeDays)
                                .environmentObject(state)
                        }
                        SurfaceCard {
                            Text("Heatmap métrique").font(.headline)
                            CalendarHeatmap(entries: filteredEntries(for: m), startDate: start, endDate: end)
                                .frame(height: 300)
                        }
                    }
                }
            } else {
                SurfaceCard {
                    EmptyStateView(text: "Choisissez une métrique pour afficher les données", systemImageName: "chart.bar")
                        .frame(maxWidth: .infinity, minHeight: 180)
                }
            }
        }
    }

    private var correlationsPanel: some View {
        SurfaceCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Associations entre métriques").font(.headline)
                    Text("Analyse locale sur les 90 derniers jours")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Button { state.refreshInsightsAndRecommendations() } label: {
                    Image(systemName: "arrow.clockwise")
                }
                    .buttonStyle(.bordered)
                    .accessibilityLabel("Recalculer les associations")
            }
            Label(
                "Une association aide à formuler une hypothèse, mais ne prouve jamais qu’une métrique en cause une autre. AJUSTE contrôle aussi la tendance générale et l’autocorrélation temporelle.",
                systemImage: "info.circle"
            )
            .font(.caption)
            .foregroundColor(.secondary)
            .fixedSize(horizontal: false, vertical: true)

            if state.correlationInsights.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.title2)
                        .foregroundColor(.secondary)
                    Text("Aucun signal suffisamment étayé")
                        .font(.subheadline.weight(.semibold))
                    Text("Enregistrez au moins 12 jours communs pour deux métriques. AJUSTE écarte les tendances trompeuses, pénalise les journées trop similaires entre elles et corrige les comparaisons multiples.")
                        .foregroundColor(.secondary)
                        .font(.caption)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
            } else {
                ForEach(Array(state.correlationInsights.prefix(6)).indices, id: \.self) { index in
                    if index > 0 {
                        Divider()
                    }
                    correlationRow(state.correlationInsights[index])
                }
            }
        }
    }

    private func correlationRow(_ insight: CorrelationInsight) -> some View {
        let evidence = insight.evidence ?? .exploratory
        let color = evidenceColor(evidence)
        let spearman = insight.spearman ?? insight.pearson
        let trendAdjusted = insight.trendAdjustedPearson ?? insight.pearson
        let effectiveSampleSize = insight.effectiveSampleSize ?? insight.sampleSize
        let interval = confidenceIntervalText(insight)
        let adjustedPValue = adjustedPValueText(insight)

        return VStack(alignment: .leading, spacing: 9) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(correlationTitle(insight))
                    .font(.subheadline.weight(.semibold))
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 8)
                Text(evidence.displayName)
                    .font(.caption2.weight(.bold))
                    .foregroundColor(color)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(color.opacity(0.12))
                    .clipShape(Capsule())
            }
            Text(insight.summary)
                .font(.caption)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            CorrelationBar(value: insight.pearson, color: color)
                .frame(height: 18)

            Text("Détails statistiques")
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)
            let stats: [(String, String)] = [
                ("Pearson r", correlationNumber(insight.pearson)),
                ("Rang ρ", correlationNumber(spearman)),
                ("Sans tendance r", correlationNumber(trendAdjusted)),
                ("Jours alignés", "\(insight.sampleSize)"),
                ("Jours utiles", "\(effectiveSampleSize)"),
                ("IC 95 %", interval),
                ("q ajusté", adjustedPValue)
            ]
            LazyVGrid(
                columns: [
                    GridItem(.flexible(minimum: 120), alignment: .leading),
                    GridItem(.flexible(minimum: 120), alignment: .leading)
                ],
                alignment: .leading,
                spacing: 8
            ) {
                ForEach(Array(stats.enumerated()), id: \.offset) { _, stat in
                    correlationStat(stat.0, value: stat.1)
                }
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    private func correlationStat(_ label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            Text(value)
                .font(.caption.monospacedDigit().weight(.semibold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(9)
        .background(
            Color(UIColor.secondarySystemBackground),
            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
        )
        .accessibilityElement(children: .combine)
    }

    private func correlationNumber(_ value: Double) -> String {
        String(format: "%.2f", value).replacingOccurrences(of: ".", with: ",")
    }

    private var experimentsPanel: some View {
        SurfaceCard {
            HStack {
                Text("Expériences N-of-1").font(.headline)
                Spacer()
                Button("Nouveau") { showingExperimentWizard = true }
                    .buttonStyle(.borderedProminent)
                    .tint(Color("Primary"))
            }
            if state.experiments.isEmpty {
                Text("Aucune expérience en cours.")
                    .foregroundColor(.secondary)
            } else {
                ForEach(state.experiments) { experiment in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(experiment.title).font(.subheadline.weight(.semibold))
                        Text(experiment.hypothesis).font(.caption).foregroundColor(.secondary)
                        HStack {
                            Button("Log aujourd'hui") {
                                logObservationToday(for: experiment)
                            }
                            .buttonStyle(.bordered)
                            .font(.caption)
                            Spacer()
                            Text(experiment.status.rawValue.capitalized)
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        if let summary = state.experimentSummary(experimentId: experiment.id),
                           let control = summary.controlAverage,
                           let intervention = summary.interventionAverage {
                            Text(String(format: "%@: %.2f • %@: %.2f • Δ %.2f",
                                        experiment.controlLabel, control,
                                        experiment.interventionLabel, intervention,
                                        summary.delta ?? 0))
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        } else {
                            Text("En attente de mesures.")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
        }
    }

    // Moyenne pour la période et le regroupement
    private func averageRow(for metric: Metric, series: [ChartSeries]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .center, spacing: 16) {
                Text("Moyenne sur la période :")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                switch grouping {
                case .metrics:
                    ForEach(series) { s in
                        let m = state.metrics.first(where: { $0.name == s.name }) ?? metric
                        let vals = dailyAverageEntries(filteredEntries(for: m)).map(\.value)
                        HStack(spacing: 6) {
                            legendSymbol(for: s)
                            if vals.isEmpty {
                                Text("\(m.name) : —")
                            } else {
                                let avg = vals.reduce(0, +) / Double(vals.count)
                                Text("\(m.name) : \(formatValue(avg, for: m))")
                            }
                        }
                        .font(.subheadline.weight(.semibold))
                    }
                case .protocols, .supplements:
                    let all = series.flatMap { $0.points.map(\.displayValue) }
                    if all.isEmpty {
                        Text("—").font(.subheadline.weight(.semibold))
                    } else {
                        let avg = all.reduce(0, +) / Double(all.count)
                        Text(String(format: "%.1f / jour", avg))
                            .font(.subheadline.weight(.semibold))
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func formatValue(_ v: Double, for m: Metric) -> String {
        let name = m.name.lowercased()
        if m.kind == .hoursMinutes || name.contains("sommeil") {
            let mins = max(0, Int(round(v)))
            let h = mins/60
            let mm = mins%60
            return String(format: "%dh%02d", h, mm)
        }
        if name.contains("poids") { return String(format: "%.1f %@", v, (m.unit ?? "kg")) }
        return String(format: "%.1f %@", v, (m.unit ?? ""))
    }

    private func averageValue(for m: Metric, series: [ChartSeries]) -> Double? {
        switch grouping {
        case .metrics:
            let vals = dailyAverageEntries(filteredEntries(for: m)).map(\.value)
            guard !vals.isEmpty else { return nil }
            return vals.reduce(0, +) / Double(vals.count)
        case .protocols, .supplements:
            let all = series.flatMap { $0.points.map(\.displayValue) }
            guard !all.isEmpty else { return nil }
            return all.reduce(0, +) / Double(all.count)
        }
    }

    private func metricsForSelection() -> [Metric] {
        state.metrics.sorted {
            $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending
        }
    }

    private func applyScreenshotMetricSelectionIfRequested() -> Bool {
#if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        guard
            let optionIndex = arguments.firstIndex(of: "-statsMetricCount"),
            arguments.indices.contains(optionIndex + 1),
            let requestedCount = Int(arguments[optionIndex + 1]),
            requestedCount > 1
        else {
            return false
        }

        let availableMetrics = metricsForSelection()
            .filter(hasData)
            .prefix(min(requestedCount, 3))
        guard let primaryMetric = availableMetrics.first else { return false }

        selectedMetricIds = Set(availableMetrics.map(\.id))
        selectedMetric = primaryMetric
        if arguments.contains("-statsBarChart") {
            chartStyle = .bar
        }
        return true
#else
        return false
#endif
    }

    private func hasData(for metric: Metric) -> Bool {
        state.metricEntries.contains(where: { $0.metricId == metric.id })
    }
    private func filteredEntries(for metric: Metric) -> [MetricEntry] {
        var entries = state.metricEntries.filter { $0.metricId == metric.id && $0.value.isFinite }
        let cal = Calendar.current
        switch range {
        case .d7:
            if let start = cal.date(byAdding: .day, value: -6, to: cal.startOfDay(for: Date())) {
                entries = entries.filter { $0.date >= start }
            }
        case .d30:
            if let start = cal.date(byAdding: .day, value: -29, to: cal.startOfDay(for: Date())) {
                entries = entries.filter { $0.date >= start }
            }
        case .d90:
            if let start = cal.date(byAdding: .day, value: -89, to: cal.startOfDay(for: Date())) {
                entries = entries.filter { $0.date >= start }
            }
        case .all:
            break
        }
        return entries.sorted { $0.date < $1.date }
    }

    private func dailyAverageEntries(_ entries: [MetricEntry]) -> [MetricEntry] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: entries) { calendar.startOfDay(for: $0.date) }
        return grouped.compactMap { day, dayEntries in
            guard !dayEntries.isEmpty else { return nil }
            let average = dayEntries.map(\.value).reduce(0, +) / Double(dayEntries.count)
            return MetricEntry(
                metricId: dayEntries[0].metricId,
                date: day,
                value: average,
                notes: nil
            )
        }
        .sorted { $0.date < $1.date }
    }

    // MARK: - Series building
    private func unitLabel(baseMetric: Metric) -> String {
        switch grouping {
        case .metrics: return baseMetric.unit ?? ""
        case .protocols, .supplements: return "fois / jour"
        }
    }

    private func metricsListForDisplay(baseMetric: Metric) -> [Metric] {
        switch grouping {
        case .metrics:
            if selectedMetricIds.isEmpty { return [baseMetric] }
            // Ordre stable basé sur l’ordre des métriques dans l’état
            return state.metrics.filter { selectedMetricIds.contains($0.id) }
        case .protocols, .supplements:
            return []
        }
    }

    private func shouldNormalizeMetricsComparison(metricsList: [Metric]) -> Bool {
        guard grouping == .metrics, metricsList.count >= 2 else { return false }
        let compatibilityKeys = Set(metricsList.map(metricCompatibilityKey))
        return compatibilityKeys.count > 1
    }

    private func metricCompatibilityKey(_ metric: Metric) -> String {
        switch metric.kind {
        case .hoursMinutes:
            return "duration_minutes"
        case .number:
            return "number:\(normalizedUnit(metric.unit))"
        }
    }

    private func normalizedUnit(_ unit: String?) -> String {
        guard let unit else { return "" }
        return unit
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .replacingOccurrences(of: " ", with: "")
            .lowercased()
    }

    private func yAxisMode(for baseMetric: Metric, isNormalizedComparison: Bool) -> MultiSeriesChart.YAxisMode {
        guard !isNormalizedComparison else { return .numeric }
        switch grouping {
        case .metrics:
            let normalizedName = baseMetric.name
                .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
                .lowercased()
            return (baseMetric.kind == .hoursMinutes || normalizedName.contains("sommeil")) ? .durationMinutes : .numeric
        case .protocols, .supplements:
            return .numeric
        }
    }

    private func tooltipUnit(for metric: Metric) -> String {
        let normalizedName = metric.name
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
            .lowercased()
        if metric.kind == .hoursMinutes || normalizedName.contains("sommeil") {
            return ""
        }
        return metric.unit ?? ""
    }

    private func buildSeries(from metricsList: [Metric], normalizeMetrics: Bool) -> [ChartSeries] {
        let cal = Calendar.current
        let startDate = dateBounds().0

        switch grouping {
        case .metrics:
            let palette = colorPalette()
            return metricsList.enumerated().map { idx, m in
                let entries = dailyAverageEntries(filteredEntries(for: m))
                let rawValues = entries.map(\.value)
                let displayValues = normalizeMetrics
                    ? CorrelationStatistics.robustStandardScores(rawValues)
                    : rawValues
                let points = zip(entries, displayValues).map { entry, display -> ChartPoint in
                    let raw = entry.value
                    return ChartPoint(date: entry.date, displayValue: display, rawValue: raw)
                }
                return ChartSeries(
                    id: m.id.uuidString,
                    name: m.name,
                    color: palette[idx % palette.count],
                    styleIndex: idx,
                    points: points,
                    rawUnit: tooltipUnit(for: m),
                    rawValueFormatter: valueFormatterForMetric(m)
                )
            }
        case .protocols:
            // Construire une série par jour planifié, y compris les jours à zéro.
            // Une journée sans complétion doit rester visible comme une absence,
            // et non disparaître comme si elle n'existait pas.
            let ids = Set(state.protocolCompletions.map { $0.protocolId })
            let items = state.protocols.filter {
                ids.contains($0.id) && (selectedProtocolId == nil || selectedProtocolId == $0.id)
            }
            let palette = colorPalette()
            return items.enumerated().map { idx, p in
                let comps = state.protocolCompletions.filter { $0.protocolId == p.id && $0.date >= startDate && p.isActive(on: $0.date) }
                let completedByDay = Dictionary(grouping: comps.filter(\.completed)) { cal.startOfDay(for: $0.date) }
                let points = daysBetween(start: startDate, end: Date()).compactMap { day -> ChartPoint? in
                    guard p.isActive(on: day), isScheduled(p.frequency, on: day) else { return nil }
                    let value = Double(completedByDay[cal.startOfDay(for: day)]?.count ?? 0)
                    return ChartPoint(date: day, displayValue: value, rawValue: value)
                }
                return ChartSeries(
                    id: p.id.uuidString,
                    name: p.name,
                    color: palette[idx % palette.count],
                    styleIndex: idx,
                    points: points,
                    rawUnit: "fois",
                    rawValueFormatter: nil
                )
            }
        case .supplements:
            let ids = Set(state.supplementIntakes.map { $0.supplementId })
            let items = state.supplements.filter {
                ids.contains($0.id) && (selectedSupplementId == nil || selectedSupplementId == $0.id)
            }
            let palette = colorPalette()
            return items.enumerated().map { idx, s in
                let ints = state.supplementIntakes.filter { $0.supplementId == s.id && $0.date >= startDate && s.isActive(on: $0.date) }
                let takenByDay = Dictionary(grouping: ints.filter(\.taken)) { cal.startOfDay(for: $0.date) }
                let points = daysBetween(start: startDate, end: Date()).compactMap { day -> ChartPoint? in
                    guard s.isActive(on: day), DailyPlanner.isScheduledToday(s.frequency, daysFallback: s.daysOfWeek, now: day) else { return nil }
                    let value = Double(takenByDay[cal.startOfDay(for: day)]?.count ?? 0)
                    return ChartPoint(date: day, displayValue: value, rawValue: value)
                }
                return ChartSeries(
                    id: s.id.uuidString,
                    name: s.name,
                    color: palette[idx % palette.count],
                    styleIndex: idx,
                    points: points,
                    rawUnit: "fois",
                    rawValueFormatter: nil
                )
            }
        }
    }

    private func daysBetween(start: Date, end: Date) -> [Date] {
        let calendar = Calendar.current
        let first = calendar.startOfDay(for: start)
        let last = calendar.startOfDay(for: end)
        var dates: [Date] = []
        var current = first
        while current <= last {
            dates.append(current)
            guard let next = calendar.date(byAdding: .day, value: 1, to: current), next > current else { break }
            current = next
        }
        return dates
    }

    private func isScheduled(_ frequency: Frequency, on date: Date) -> Bool {
        switch frequency {
        case .daily, .timesPerDay:
            return true
        case .weekly(let days):
            let selected = Set(days)
            return !selected.isEmpty && selected.contains(DailyPlanner.currentWeekdayMon1ToSun7(now: date))
        }
    }

    private func colorPalette() -> [Color] { [.blue, .green, .orange, .pink, .purple, .teal, .indigo, .brown, .mint, .red] }

    private func dateBounds() -> (Date, Date) {
        let cal = Calendar.current
        let end = Date()
        switch range {
        case .d7: return (cal.date(byAdding: .day, value: -6, to: cal.startOfDay(for: end)) ?? end, end)
        case .d30: return (cal.date(byAdding: .day, value: -29, to: cal.startOfDay(for: end)) ?? end, end)
        case .d90: return (cal.date(byAdding: .day, value: -89, to: cal.startOfDay(for: end)) ?? end, end)
        case .all: return (earliestRelevantDate ?? (cal.date(byAdding: .day, value: -29, to: end) ?? end), end)
        }
    }

    private var rangeDays: Int {
        switch range {
        case .d7: return 7
        case .d30: return 30
        case .d90: return 90
        case .all:
            guard let earliestRelevantDate else { return 30 }
            let days = Calendar.current.dateComponents(
                [.day],
                from: Calendar.current.startOfDay(for: earliestRelevantDate),
                to: Calendar.current.startOfDay(for: Date())
            ).day ?? 29
            return max(1, days + 1)
        }
    }

    private var earliestRelevantDate: Date? {
        switch grouping {
        case .metrics:
            let ids = selectedMetricIds.isEmpty
                ? Set(state.metrics.map(\.id))
                : selectedMetricIds
            return state.metricEntries
                .filter { ids.contains($0.metricId) }
                .map(\.date)
                .min()
        case .protocols:
            return state.protocolCompletions
                .filter { selectedProtocolId == nil || $0.protocolId == selectedProtocolId }
                .map(\.date)
                .min()
        case .supplements:
            return state.supplementIntakes
                .filter { selectedSupplementId == nil || $0.supplementId == selectedSupplementId }
                .map(\.date)
                .min()
        }
    }

    private func metricName(for id: UUID) -> String {
        state.metrics.first(where: { $0.id == id })?.name ?? "—"
    }

    private func logObservationToday(for experiment: NOf1Experiment) {
        let todayEntries = state.metricEntries
            .filter { $0.metricId == experiment.targetMetricId && Calendar.current.isDateInToday($0.date) }
            .sorted { $0.date > $1.date }
        let fallbackEntries = state.metricEntries
            .filter { $0.metricId == experiment.targetMetricId }
            .sorted { $0.date > $1.date }
        let value = todayEntries.first?.value ?? fallbackEntries.first?.value ?? 0
        state.recordObservation(experimentId: experiment.id, value: value, date: Date(), notes: nil)
    }

    private func customTicks() -> [Date]? {
        let cal = Calendar.current
        let (start, end) = dateBounds()
        let days = max(1, Int((cal.startOfDay(for: end).timeIntervalSince1970 - cal.startOfDay(for: start).timeIntervalSince1970) / 86400))
        var step = 1
        if days <= 7 { step = 1 }
        else if days <= 30 { step = 5 }
        else if days <= 90 { step = 10 }
        else { step = days / 6 }
        var arr: [Date] = []
        var d = cal.startOfDay(for: start)
        while d <= end {
            arr.append(d)
            guard let next = cal.date(byAdding: .day, value: max(step, 1), to: d), next > d else { break }
            d = next
        }
        arr.append(cal.startOfDay(for: end))
        return Array(Set(arr)).sorted()
    }

    // Forcer éventuellement une plage Y (sinon laisser dynamique au composant)
    private func forcedYRange(for m: Metric, series: [ChartSeries]) -> (Double?, Double?) {
        let name = m.name.lowercased()
        // Heures/minutes (ex: sommeil): bornes alignées sur des multiples de 30 minutes
        if m.kind == .hoursMinutes || name.contains("sommeil") {
            let values = series.flatMap { $0.points.map { max(0, $0.displayValue) } }
            guard let minVal0 = values.min(), let maxVal0 = values.max() else { return (0, 16*60) }
            let step: Double = 30
            let yMin = floor(minVal0 / step) * step
            var yMax = ceil(maxVal0 / step) * step
            if yMax - yMin < step * 4 { yMax = yMin + step * 4 } // au moins 2h de hauteur
            return (yMin, yMax)
        }
        if name.contains("poids") {
            let values = series.flatMap { $0.points.map(\.displayValue) }
            if let minimum = values.min(), let maximum = values.max() {
                let padding = max(0.5, (maximum - minimum) * 0.2)
                return (minimum - padding, maximum + padding)
            }
        }
        return (nil, nil)
    }

    private func legendRow(series: [ChartSeries]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(series) { s in
                    HStack(spacing: 6) {
                        legendSymbol(for: s)
                        Text(s.name).font(.caption)
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func seriesContainTemporalGaps(_ series: [ChartSeries]) -> Bool {
        let calendar = Calendar.current
        return series.contains { item in
            let dates = item.points.map(\.date).sorted()
            return zip(dates, dates.dropFirst()).contains { previous, current in
                let days = calendar.dateComponents(
                    [.day],
                    from: calendar.startOfDay(for: previous),
                    to: calendar.startOfDay(for: current)
                ).day ?? 0
                return days > 1
            }
        }
    }

    private func legendSymbol(for series: ChartSeries) -> some View {
        let symbolName: String
        switch series.styleIndex % 3 {
        case 1: symbolName = "square.fill"
        case 2: symbolName = "diamond.fill"
        default: symbolName = "circle.fill"
        }
        return Image(systemName: symbolName)
            .font(.system(size: 8, weight: .bold))
            .foregroundColor(series.color)
            .frame(width: 10, height: 10)
            .accessibilityHidden(true)
    }

    private var chartSectionTitle: String {
        switch grouping {
        case .metrics: return "Évolution des métriques"
        case .protocols: return "Réalisation des protocoles"
        case .supplements: return "Prises de suppléments"
        }
    }

    private var periodDescription: String {
        switch range {
        case .d7: return "7 derniers jours"
        case .d30: return "30 derniers jours"
        case .d90: return "90 derniers jours"
        case .all: return "Toutes les données disponibles"
        }
    }

    private func correlationTitle(_ insight: CorrelationInsight) -> String {
        let metricA = metricName(for: insight.metricAId)
        let metricB = metricName(for: insight.metricBId)
        if insight.lagDays > 0 {
            return "\(metricA) → \(metricB) · +\(insight.lagDays) j"
        }
        if insight.lagDays < 0 {
            return "\(metricB) → \(metricA) · +\(abs(insight.lagDays)) j"
        }
        return "\(metricA) ↔ \(metricB) · même jour"
    }

    private func evidenceColor(_ evidence: CorrelationEvidence) -> Color {
        switch evidence {
        case .exploratory: return .orange
        case .moderate: return .blue
        case .strong: return .green
        }
    }

    private func confidenceIntervalText(_ insight: CorrelationInsight) -> String {
        guard let lower = insight.confidenceLower, let upper = insight.confidenceUpper else {
            return "—"
        }
        return "[\(correlationNumber(lower)) ; \(correlationNumber(upper))]"
    }

    private func adjustedPValueText(_ insight: CorrelationInsight) -> String {
        guard let value = insight.adjustedPValue else { return "—" }
        if value < 0.001 {
            return "< 0,001"
        }
        return String(format: "%.3f", value)
            .replacingOccurrences(of: ".", with: ",")
    }

    private func valueFormatterForMetric(_ m: Metric) -> ((Double) -> String)? {
        let name = m.name.lowercased()
        if m.kind == .hoursMinutes || name.contains("sommeil") {
            return { v in
                let mins = max(0, Int(round(v)))
                let h = mins / 60
                let mm = mins % 60
                return String(format: "%dh%02d", h, mm)
            }
        }
        if name.contains("poids") {
            return { v in String(format: "%.1f", v) }
        }
        if m.unit == "ms" {
            return { v in String(format: "%.1f", v) }
        }
        return { v in String(format: "%.0f", v) }
    }

    func share(text: String) {
        let av = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }),
              let root = scene.windows.first(where: \.isKeyWindow)?.rootViewController else {
            return
        }
        var presenter = root
        while let presented = presenter.presentedViewController {
            presenter = presented
        }
        av.popoverPresentationController?.sourceView = presenter.view
        presenter.present(av, animated: true)
    }
}

private struct CorrelationBar: View {
    let value: Double
    let color: Color

    var body: some View {
        GeometryReader { proxy in
            let width = max(proxy.size.width, 1)
            let center = width / 2
            let clamped = min(1, max(-1, value))
            let magnitude = abs(clamped) * center
            let start = clamped >= 0 ? center : center - magnitude

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color.secondary.opacity(0.10))
                Rectangle()
                    .fill(Color.secondary.opacity(0.35))
                    .frame(width: 1)
                    .offset(x: center)
                Capsule()
                    .fill(color.opacity(0.75))
                    .frame(width: max(magnitude, 2))
                    .offset(x: start)
            }
            .overlay {
                HStack {
                    Text("−")
                    Spacer()
                    Text("+")
                }
                .font(.caption2.weight(.bold))
                .foregroundColor(.secondary)
                .padding(.horizontal, 5)
            }
        }
        .accessibilityLabel("Coefficient de corrélation de Pearson")
        .accessibilityValue(
            "\(value >= 0 ? "Association positive" : "Association négative"), \(String(format: "%.2f", value).replacingOccurrences(of: ".", with: ",")) sur une échelle de moins un à plus un"
        )
    }
}

#Preview { StatsView().environmentObject(AppState()) }
