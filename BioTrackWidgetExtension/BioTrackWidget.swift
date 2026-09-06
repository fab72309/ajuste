import SwiftUI
import WidgetKit

struct BioTrackWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetDailySnapshot
}

struct BioTrackWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> BioTrackWidgetEntry {
        BioTrackWidgetEntry(
            date: Date(),
            snapshot: WidgetDailySnapshot(
                date: Date(),
                progressDone: 1,
                progressTotal: 3,
                priorityItems: [
                    WidgetPriorityItem(
                        itemId: UUID(),
                        kind: .protocolItem,
                        title: "Meditation matinale",
                        isDone: false,
                        subtitle: "Quotidien",
                        preferredMinutes: 420
                    )
                ],
                upcomingReminders: [
                    WidgetReminderItem(reminderId: UUID(), title: "Vitamine D3", hour: 8, minute: 0, enabled: true)
                ],
                routineProfileName: "Semaine",
                recommendations: ["Check-in du matin"]
            )
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (BioTrackWidgetEntry) -> Void) {
        completion(BioTrackWidgetEntry(date: Date(), snapshot: loadSnapshot()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<BioTrackWidgetEntry>) -> Void) {
        let now = Date()
        let entry = BioTrackWidgetEntry(date: now, snapshot: loadSnapshot(now: now))
        let next = Calendar.current.date(byAdding: .minute, value: 15, to: now) ?? now.addingTimeInterval(900)
        completion(Timeline(entries: [entry], policy: .after(next)))
    }

    private func loadSnapshot(now: Date = Date()) -> WidgetDailySnapshot {
        let snapshot = SharedStore.load()
        return DailyPlanner.buildWidgetSnapshot(from: snapshot, now: now)
    }
}

struct BioTrackWidget: Widget {
    private let kind = "BioTrackWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: BioTrackWidgetProvider()) { entry in
            BioTrackWidgetView(entry: entry)
                .containerBackground(.fill.tertiary, for: .widget)
                .widgetURL(URL(string: "biotrack://home"))
        }
        .configurationDisplayName("AJUSTE au quotidien")
        .description("Objectifs du jour et rappels avec actions rapides.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

private struct BioTrackWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: BioTrackWidgetEntry

    var body: some View {
        switch family {
        case .systemSmall:
            smallView
        case .systemMedium:
            mediumView
        default:
            largeView
        }
    }

    private var header: some View {
        HStack {
            Text(entry.snapshot.routineProfileName ?? "Objectifs")
                .font(.caption.weight(.semibold))
            Spacer()
            Text(progressText)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.secondary)
        }
    }

    private var progressText: String {
        "\(entry.snapshot.progressDone)/\(entry.snapshot.progressTotal)"
    }

    private var progressPercent: Int {
        guard entry.snapshot.progressTotal > 0 else { return 0 }
        return Int((Double(entry.snapshot.progressDone) / Double(entry.snapshot.progressTotal)) * 100)
    }

    private var smallView: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            ProgressView(value: Double(entry.snapshot.progressDone), total: Double(max(entry.snapshot.progressTotal, 1)))
                .tint(.teal)
            Text("\(progressPercent)% complété")
                .font(.caption2)
                .foregroundStyle(.secondary)
            if let first = entry.snapshot.priorityItems.first {
                priorityActionRow(item: first)
            } else {
                Text("Aucun objectif aujourd'hui")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let recommendation = entry.snapshot.recommendations.first {
                Text(recommendation)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var mediumView: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 8) {
                header
                ProgressView(value: Double(entry.snapshot.progressDone), total: Double(max(entry.snapshot.progressTotal, 1)))
                    .tint(.teal)
                ForEach(Array(entry.snapshot.priorityItems.prefix(2))) { item in
                    priorityActionRow(item: item)
                }
                if entry.snapshot.priorityItems.isEmpty {
                    Text("Aucun objectif aujourd'hui")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            VStack(alignment: .leading, spacing: 8) {
                Link(destination: URL(string: "biotrack://reminders")!) {
                    Text("Rappels")
                        .font(.caption.weight(.semibold))
                }
                if let reminder = entry.snapshot.upcomingReminders.first {
                    reminderActionRow(item: reminder)
                } else {
                    Text("Aucun rappel")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var largeView: some View {
        VStack(alignment: .leading, spacing: 10) {
            header
            ProgressView(value: Double(entry.snapshot.progressDone), total: Double(max(entry.snapshot.progressTotal, 1)))
                .tint(.teal)

            Text("Objectifs prioritaires")
                .font(.caption.weight(.semibold))
            if entry.snapshot.priorityItems.isEmpty {
                Text("Aucun objectif aujourd'hui")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(entry.snapshot.priorityItems.prefix(4))) { item in
                    priorityActionRow(item: item)
                }
            }

            Text("Rappels")
                .font(.caption.weight(.semibold))
            if entry.snapshot.upcomingReminders.isEmpty {
                Text("Aucun rappel")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(Array(entry.snapshot.upcomingReminders.prefix(3))) { item in
                    reminderActionRow(item: item)
                }
            }

            if !entry.snapshot.recommendations.isEmpty {
                Text("Reco")
                    .font(.caption.weight(.semibold))
                ForEach(Array(entry.snapshot.recommendations.prefix(2)), id: \.self) { title in
                    Text("• \(title)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    @ViewBuilder
    private func priorityActionRow(item: WidgetPriorityItem) -> some View {
        HStack(spacing: 8) {
            switch item.kind {
            case .protocolItem:
                Button(intent: ToggleProtocolCompletionIntent(protocolId: item.itemId.uuidString)) {
                    Image(systemName: item.isDone ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(item.isDone ? .green : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.isDone ? "Marquer protocole non fait" : "Marquer protocole fait")
            case .supplement:
                Button(intent: ToggleSupplementIntakeIntent(supplementId: item.itemId.uuidString)) {
                    Image(systemName: item.isDone ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(item.isDone ? .green : .secondary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(item.isDone ? "Marquer complément non pris" : "Marquer complément pris")
            }

            VStack(alignment: .leading, spacing: 1) {
                Text(item.title)
                    .font(.caption)
                    .lineLimit(1)
                if let subtitle = item.subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        }
    }

    private func reminderActionRow(item: WidgetReminderItem) -> some View {
        HStack(spacing: 8) {
            Button(intent: ToggleReminderEnabledIntent(reminderId: item.reminderId.uuidString)) {
                Image(systemName: item.enabled ? "bell.fill" : "bell.slash")
                    .foregroundStyle(item.enabled ? .teal : .secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(item.enabled ? "Désactiver rappel" : "Activer rappel")

            Text(item.title)
                .font(.caption)
                .lineLimit(1)
            Spacer()
            Text(String(format: "%02d:%02d", item.hour, item.minute))
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
    }
}
