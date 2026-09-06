import SwiftUI

struct SupplementsView: View {
	@EnvironmentObject var state: AppState
	@State private var showingAdd = false
	@State private var editingSupplement: Supplement? = nil
	@State private var searchText: String = ""
	@State private var showingFilter = false
	@State private var activeOnly: Bool = false
	@State private var categoryFilters: Set<String> = []
	@State private var toDeleteSupplement: Supplement? = nil
	@State private var showDeleteAlert: Bool = false
	@State private var showingLibrary: Bool = false

	var body: some View {
		NavigationView {
			ZStack {
				VStack(spacing: 8) {
					// Recherche + actions
					HStack(spacing: 8) {
						SearchField(placeholder: "Rechercher des compléments...", text: $searchText)
							.frame(maxWidth: .infinity)
						Button { showingLibrary = true } label: { Image(systemName: "chart.bar.doc.horizontal").imageScale(.large) }
						Button { showingFilter = true } label: { Image(systemName: "line.3.horizontal.decrease.circle").imageScale(.large) }
							.overlay(alignment: .topTrailing) {
								if isFilterActive { Circle().fill(Color("Primary")).frame(width: 8, height: 8).offset(x: 4, y: -4) }
							}
					}
					.padding(.horizontal, 16)
					.padding(.top, 6)

					// Barre de catégories (chips)
					ScrollView(.horizontal, showsIndicators: false) {
						HStack(spacing: 8) {
							SelectableChip(title: "Toutes", selected: categoryFilters.isEmpty) { categoryFilters.removeAll() }
							ForEach(supplementCategories(), id: \.self) { cat in
								let selected = categoryFilters == [cat.lowercased()]
								SelectableChip(title: cat,
											selected: selected,
											iconSystemName: CategoryAppearance.iconName(for: cat),
											tintColor: CategoryAppearance.color(for: cat),
											action: { categoryFilters = [cat.lowercased()] })
							}
						}
						.padding(.horizontal, 16)
					}

					// Badges de filtre actifs
					if isFilterActive {
						HStack(spacing: 6) {
							if activeOnly { chip("Actifs uniquement") }
							ForEach(Array(categoryFilters).sorted(), id: \.self) { c in chip(c.capitalized) }
							Spacer()
							Button("Réinitialiser") { resetFilters() }.font(.caption2)
						}
						.padding(.horizontal, 16)
					}

					List {
						ForEach(groupedAndFiltered().keys.sorted(), id: \.self) { cat in
							Section(header: sectionHeader(for: cat)) {
								ForEach(groupedAndFiltered()[cat] ?? []) { s in
									SwipeableRow(
										onEdit: { editingSupplement = s },
										onDelete: { toDeleteSupplement = s; showDeleteAlert = true },
										onTap: { editingSupplement = s }
									) {
										HStack(alignment: .top) {
											VStack(alignment: .leading, spacing: 2) {
												Text(s.name).font(.headline)
												HStack {
													if let dose = s.dose { Text(dose) }
													if let brand = s.brand { Text("• \(brand)") }
												}
												.foregroundColor(.secondary)
											}
											Spacer(minLength: 8)
											Button(action: { state.toggleSupplementActivation(s.id) }) {
												Text(s.active ? "Activé" : "Inactif")
													.font(.caption2.weight(.semibold))
													.padding(.horizontal, 8)
													.padding(.vertical, 4)
													.background((s.active ? Color("Primary") : Color.gray).opacity(0.15))
													.foregroundColor(s.active ? Color("Primary") : .gray)
													.clipShape(Capsule())
											}
											.buttonStyle(.plain)
										}
										.padding(.vertical, 8)
										.transition(.move(edge: .trailing).combined(with: .opacity))
									}
							}
						}
					}
					.listStyle(.insetGrouped)
					}
					.bioTrackConfirmation(
						isPresented: $showDeleteAlert,
						title: "Supprimer le supplément ?",
						message: deleteSupplementConfirmationMessage,
						confirmTitle: "Supprimer",
						isDestructive: true
					) {
						guard let supplement = toDeleteSupplement,
							  let idx = state.supplements.firstIndex(where: { $0.id == supplement.id }) else { return }
						state.supplements.remove(at: idx)
						state.save()
						toDeleteSupplement = nil
					}
				}

				FloatingActionButton { showingAdd = true }
					.padding(.trailing, 24)
					.padding(.bottom, 32)
					.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
			}
			.navigationTitle(Text(NSLocalizedString("tab.supplements", comment: "")))
			.sheet(isPresented: $showingFilter) {
				SupplementFilterSheet(currentActiveOnly: activeOnly, currentCategories: categoryFilters) { active, cats in
					activeOnly = active
					categoryFilters = cats
				}
				.withSheetDetentsIfAvailable()
			}
			.sheet(isPresented: $showingAdd) {
				AddSupplementForm { newSupplement, customReminder in
					state.supplements.append(newSupplement)
					state.save()
					if let customReminder {
						saveCustomReminder(customReminder)
					}
				}
				.withSheetDetentsIfAvailable()
			}
			.sheet(item: $editingSupplement) { sup in
				AddSupplementForm(existing: sup) { updated, _ in
					if let idx = state.supplements.firstIndex(where: { $0.id == sup.id }) {
						var targetIndex = idx
						var current = state.supplements[targetIndex]
						if updated.active != current.active {
							state.toggleSupplementActivation(sup.id)
							if let refreshedIdx = state.supplements.firstIndex(where: { $0.id == sup.id }) {
								targetIndex = refreshedIdx
								current = state.supplements[targetIndex]
							}
						}
						current.name = updated.name
						current.brand = updated.brand
						current.dose = updated.dose
						current.category = updated.category
						current.timeOfDay = updated.timeOfDay
						current.timeContext = updated.timeContext
						current.frequency = updated.frequency
						current.timesPerDay = updated.timesPerDay
						current.daysOfWeek = updated.daysOfWeek
						current.durationNote = updated.durationNote
						current.notes = updated.notes
						state.supplements[targetIndex] = current
						state.save()
					}
				}
				.withSheetDetentsIfAvailable()
			}
			.sheet(isPresented: $showingLibrary) {
				NavigationView { SupplementLibrarySheet().environmentObject(state) }.withSheetDetentsIfAvailable()
			}
		}
	}
}

// MARK: - Helpers
extension SupplementsView {
	private var isFilterActive: Bool { !categoryFilters.isEmpty || activeOnly }

	private var deleteSupplementConfirmationMessage: String {
		guard let supplement = toDeleteSupplement else { return "Cette action est irréversible." }
		return "Supprimer ‘\(supplement.name)’ ? Cette action est irréversible."
	}

	private func groupedAndFiltered() -> [String: [Supplement]] {
		var items = state.supplements
		if activeOnly { items = items.filter { $0.isActive(on: Date()) } }
		if !searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
			let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
			items = items.filter { s in s.name.localizedCaseInsensitiveContains(q) || (s.brand?.localizedCaseInsensitiveContains(q) ?? false) || (s.dose?.localizedCaseInsensitiveContains(q) ?? false) }
		}
		if !categoryFilters.isEmpty { items = items.filter { s in (s.category?.lowercased()).map { categoryFilters.contains($0) } ?? false } }
		let todaysWeekday = ((Calendar.current.component(.weekday, from: Date()) + 5) % 7) + 1
		func scheduledToday(_ s: Supplement) -> Bool {
			// The catalog should keep ad-hoc supplements visible even though they
			// are intentionally absent from the daily checklist.
			if s.frequency.isAsNeeded { return true }
			switch s.frequency { case .daily, .timesPerDay: return true; case .weekly(let days): let set = Set((!days.isEmpty ? days : (s.daysOfWeek ?? [])).map { $0 }); return !set.isEmpty && set.contains(todaysWeekday) }
		}
		items = items.filter { scheduledToday($0) }
		var grouped: [String: [Supplement]] = [:]
		for s in items { grouped[s.category ?? "Autre", default: []].append(s) }
		for (k, v) in grouped { grouped[k] = v.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } }
		return grouped
	}

	private func supplementCategories() -> [String] { Array(Set(state.supplements.compactMap { $0.category })).sorted() }

	private func sectionHeader(for category: String) -> some View {
		HStack(spacing: 8) {
			Image(systemName: categoryIconName(for: category)).foregroundColor(categoryColor(for: category))
			Text(category).font(.headline)
			Spacer()
		}
	}

	private func chip(_ text: String) -> some View {
		Text(text).font(.caption2).padding(.horizontal, 8).padding(.vertical, 4).background(Color(UIColor.secondarySystemBackground)).clipShape(Capsule())
	}

	private func categoryChip(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
		Button(action: action) {
			HStack(spacing: 6) {
				Image(systemName: categoryIconName(for: title)).foregroundColor(selected ? Color("OnPrimary") : categoryColor(for: title))
				Text(title)
			}
			.font(.footnote)
			.padding(.horizontal, 10)
			.padding(.vertical, 6)
			.background(selected ? Color("Primary") : Color(UIColor.secondarySystemBackground))
			.foregroundColor(selected ? Color("OnPrimary") : .primary)
			.clipShape(Capsule())
		}
		.buttonStyle(.plain)
	}

	private func resetFilters() { activeOnly = false; categoryFilters.removeAll() }

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

private func categoryIconName(for category: String) -> String {
	let key = category.lowercased()
	switch key {
	case "nootropiques", "nootropics": return "brain.head.profile"
	case "vitamines", "vitamins": return "asterisk.circle"
	case "minéraux", "minerals": return "flask"
	case "protéines", "proteins", "protéine": return "dumbbell"
	case "énergie", "energy": return "bolt"
	case "récupération", "recovery": return "heart"
	case "sommeil", "sleep": return "moon"
	case "digestif", "digestive": return "fork.knife"
	case "immunité", "immune": return "shield"
	case "traitement médical", "traitement", "medical": return "pills"
	default: return "circle.grid.3x3"
	}
}

private func categoryColor(for category: String) -> Color {
	let key = category.lowercased()
	switch key {
	case "nootropiques", "nootropics": return .purple
	case "vitamines", "vitamins": return .yellow
	case "minéraux", "minerals": return .teal
	case "protéines", "proteins", "protéine": return .orange
	case "énergie", "energy": return .pink
	case "récupération", "recovery": return .green
	case "sommeil", "sleep": return .indigo
	case "digestif", "digestive": return .brown
	case "immunité", "immune": return .mint
	case "traitement médical", "traitement", "medical": return .red
	default: return .secondary
	}
}

#Preview { SupplementsView().environmentObject(AppState()) }
