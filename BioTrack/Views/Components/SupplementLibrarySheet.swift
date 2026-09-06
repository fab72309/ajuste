import SwiftUI

// MARK: - Library Models

enum LibraryCategory: String, Codable, CaseIterable, Identifiable {
    case nootropics = "Nootropiques"
    case vitamins   = "Vitamines"
    case minerals   = "Minéraux"
    case performance = "Énergie"
    case sleep      = "Sommeil"
    var id: String { rawValue }
}

struct LibrarySupplement: Identifiable, Codable {
    let id: String
    let name: String
    let categories: [LibraryCategory]
}

let supplementLibrary: [LibrarySupplement] = [
    .init(
        id: "vitD3",
        name: "Vitamine D3",
        categories: [.vitamins]
    ),
    .init(
        id: "vitB12",
        name: "Vitamine B12",
        categories: [.vitamins]
    ),
    .init(
        id: "vitC",
        name: "Vitamine C",
        categories: [.vitamins]
    ),
    .init(
        id: "mgGly",
        name: "Magnésium (glycinate)",
        categories: [.minerals]
    ),
    .init(
        id: "zinc",
        name: "Zinc (picolinate/citrate)",
        categories: [.minerals]
    ),
    .init(
        id: "omega3",
        name: "Oméga-3 (EPA+DHA)",
        categories: [.performance]
    ),
    .init(
        id: "creatine",
        name: "Créatine monohydrate",
        categories: [.performance]
    ),
    .init(
        id: "theanineCaffeine",
        name: "L-théanine + caféine",
        categories: [.nootropics]
    ),
    .init(
        id: "rhodiola",
        name: "Rhodiola rosea (SHR-5)",
        categories: [.nootropics]
    ),
    .init(
        id: "bacopa",
        name: "Bacopa monnieri (≥45% bacosides)",
        categories: [.nootropics]
    ),
    .init(
        id: "melatonin",
        name: "Mélatonine",
        categories: [.sleep]
    )
]

// MARK: - Sheet UI

struct SupplementLibrarySheet: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var state: AppState

    @State private var searchText: String = ""
    @State private var selectedCategory: LibraryCategory? = nil
    @State private var showingCustomReminderSheet = false
    @State private var customReminderInitialData: ReminderSheet.ReminderData? = nil

    var body: some View {
        VStack(spacing: 10) {
            searchBar
            categoryChips
            Label(
                "Ce catalogue ne recommande aucun produit ni dosage. Ajoutez uniquement les éléments que vous avez choisis et renseignez votre propre produit.",
                systemImage: "cross.case"
            )
            .font(.caption)
            .foregroundColor(.secondary)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.horizontal, 12)
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(filteredLibrary) { item in
                        LibraryCard(
                            item: item,
                            isAdded: isAdded(item),
                            onAdd: { _ = addToUser(item) },
                            onAddWithReminder: { addFromLibraryWithCustomReminder(item) }
                        )
                    }
                }
                .padding(.horizontal, 12)
                .padding(.top, 4)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Catalogue de suppléments")
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
            }
        }
        .sheet(isPresented: $showingCustomReminderSheet) {
            ReminderSheet(initialData: customReminderInitialData) { data in
                saveCustomReminder(data)
                customReminderInitialData = nil
            }
            .withSheetDetentsIfAvailable()
        }
    }

    // MARK: - Views
    private var searchBar: some View {
        SearchField(placeholder: "Rechercher des compléments...", text: $searchText)
            .padding(.horizontal, 12)
            .padding(.top, 8)
    }

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                categoryChip(title: "Tous", selected: selectedCategory == nil) { selectedCategory = nil }
                ForEach(LibraryCategory.allCases) { cat in
                    chipForCategory(cat, selected: selectedCategory == cat) { selectedCategory = cat }
                }
            }
            .padding(.horizontal, 12)
        }
    }

    private func chipForCategory(_ category: LibraryCategory, selected: Bool, action: @escaping () -> Void) -> some View {
        let title = category.rawValue
        return SelectableChip(title: title,
                              selected: selected,
                              iconSystemName: CategoryAppearance.iconName(for: title),
                              tintColor: CategoryAppearance.color(for: title),
                              action: action)
    }

    private func categoryChip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        SelectableChip(title: title, selected: selected, action: action)
    }

    // MARK: - Helpers
    private var filteredLibrary: [LibrarySupplement] {
        let base: [LibrarySupplement]
        if let cat = selectedCategory { base = supplementLibrary.filter { $0.categories.contains(cat) } } else { base = supplementLibrary }
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return base }
        return base.filter { s in
            s.name.localizedCaseInsensitiveContains(q) ||
                s.categories.contains(where: { $0.rawValue.localizedCaseInsensitiveContains(q) })
        }
    }

    private func iconForCategory(_ cat: LibraryCategory) -> String {
        switch cat {
        case .nootropics: return "brain.head.profile"
        case .vitamins: return "asterisk.circle"
        case .minerals: return "flask"
        case .performance: return "bolt"
        case .sleep: return "moon"
        }
    }

    private func colorForCategory(_ title: String) -> Color {
        let key = title.lowercased()
        switch key {
        case "nootropiques", "nootropics": return .purple
        case "vitamines", "vitamins": return .yellow
        case "minéraux", "minerals", "mineraux": return .teal
        case "protéines", "proteins", "proteine": return .orange
        case "énergie", "energy", "performance", "energie": return .pink
        case "récupération", "recovery", "recuperation": return .green
        case "sommeil", "sleep": return .indigo
        case "digestif", "digestive": return .brown
        case "immunité", "immune", "immunite": return .mint
        case "traitement médical", "traitement", "medical": return .red
        default: return .secondary
        }
    }

    private func isAdded(_ item: LibrarySupplement) -> Bool {
        state.supplements.contains { $0.name.localizedCaseInsensitiveCompare(item.name) == .orderedSame }
    }

    @discardableResult
    private func addToUser(_ item: LibrarySupplement) -> Supplement? {
        guard !isAdded(item) else { return nil }
        let categoryTitle: String = item.categories.first?.rawValue ?? "Autre"
        let newSup = Supplement(
            name: item.name,
            brand: nil,
            dose: nil,
            category: categoryTitle,
            timeOfDay: nil,
            timeContext: nil,
            frequency: .daily,
            timesPerDay: nil,
            daysOfWeek: nil,
            durationNote: nil,
            notes: "Fiche de suivi à personnaliser avec les informations de votre propre produit.",
            active: true
        )
        state.supplements.append(newSup)
        state.save()
        return newSup
    }

    private func addFromLibraryWithCustomReminder(_ item: LibrarySupplement) {
        guard let supplement = addToUser(item) else { return }
        customReminderInitialData = defaultReminderData(for: supplement.name)
        showingCustomReminderSheet = true
    }

    private func defaultReminderData(for name: String) -> ReminderSheet.ReminderData {
        let defaultTime = Calendar.current.date(bySettingHour: 8, minute: 0, second: 0, of: Date()) ?? Date()
        return ReminderSheet.ReminderData(
            title: "Suivi : \(name)",
            time: defaultTime,
            days: Set(1...7),
            description: "",
            notificationsEnabled: true
        )
    }

    private func saveCustomReminder(_ data: ReminderSheet.ReminderData) {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: data.time)
        let reminder = Reminder(
            title: data.title,
            hour: comps.hour ?? 8,
            minute: comps.minute ?? 0,
            weekdays: Array(data.days).sorted(),
            notes: data.description,
            enabled: data.notificationsEnabled
        )
        state.reminders.append(reminder)
        state.save()
        if reminder.enabled {
            NotificationService.shared.scheduleReminder(reminder)
        }
    }
}

// MARK: - Card

private struct LibraryCard: View {
    let item: LibrarySupplement
    let isAdded: Bool
    let onAdd: () -> Void
    let onAddWithReminder: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(item.name)
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 6) {
                    if let first = item.categories.first {
                        HStack(spacing: 6) {
                            Image(systemName: iconForCategory(first))
                            Text(first.rawValue)
                        }
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(UIColor.secondarySystemBackground))
                        .clipShape(Capsule())
                    }
                }
            }
            Text("Fiche de suivi à personnaliser")
                .font(.footnote.weight(.semibold))
            Text("Aucune dose ni bénéfice n’est proposé par AJUSTE.")
                .font(.caption)
                .foregroundColor(.secondary)
            HStack {
                Spacer()
                if isAdded {
                    Label("Ajouté", systemImage: "checkmark.circle.fill")
                        .font(.caption)
                        .foregroundColor(.green)
                } else {
                    HStack(spacing: 8) {
                        Button(action: onAdd) {
                            Label("Ajouter", systemImage: "plus")
                                .font(.caption)
                        }
                        Button(action: onAddWithReminder) {
                            Label("Ajouter + rappel", systemImage: "bell.badge")
                                .font(.caption)
                        }
                    }
                }
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(Color(UIColor.secondarySystemBackground)))
    }

    private func iconForCategory(_ cat: LibraryCategory) -> String {
        switch cat {
        case .nootropics: return "brain.head.profile"
        case .vitamins: return "asterisk.circle"
        case .minerals: return "flask"
        case .performance: return "bolt"
        case .sleep: return "moon"
        }
    }
}
