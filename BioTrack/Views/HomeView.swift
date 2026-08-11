import SwiftUI

struct HomeView: View {
    @EnvironmentObject var state: AppState
    @AppStorage("selectedTab") private var selectedTab: Int = 0
    @AppStorage("showRecommendationsCard") private var showRecommendationsCard: Bool = true
    @AppStorage("collapseRecommendationsCard") private var collapseRecommendationsCard: Bool = false
    @AppStorage(CheckInMetricSelection.storageKey) private var selectedCheckInMetricIdsRaw: String = ""

    @State private var selectedProtocol: ProtocolItem? = nil
    @State private var selectedSupplement: Supplement? = nil

    @State private var showSettings = false
    @State private var showingObjectives = false
    @State private var showingMorningCheckIn = false
    @State private var showingEveningCheckIn = false
    @State private var showingStreakAchievements = false
    @State private var showingCheckInMetricSelection = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if showRecommendationsCard {
                        recommendationsCard
                    }
                    checkInCard
                    remindersCard
                    objectivesCard
                    protocolsSection
                    supplementsSection
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    brandMark
                }
                ToolbarItem(placement: .primaryAction) {
                    Button(action: { showSettings = true }) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundColor(Color("Primary"))
                            .frame(width: 36, height: 36)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Paramètres")
                }
            }
        }
        .sheet(isPresented: $showSettings) { SettingsView().withSheetDetentsIfAvailable() }
        .sheet(isPresented: $showingMorningCheckIn) {
            DailyCheckInSheet(
                period: .morning,
                existing: state.checkIn(for: .morning),
                selectedMetrics: selectedCheckInMetrics,
                existingMetricValues: checkInMetricValues(for: .morning)
            ) { input in
                state.upsertCheckIn(
                    period: .morning,
                    energy: input.energy,
                    mood: input.mood,
                    sleepQuality: input.sleepQuality,
                    note: input.note,
                    customMetricValues: input.metricValues
                )
            }.withSheetDetentsIfAvailable()
        }
        .sheet(isPresented: $showingEveningCheckIn) {
            DailyCheckInSheet(
                period: .evening,
                existing: state.checkIn(for: .evening),
                selectedMetrics: selectedCheckInMetrics,
                existingMetricValues: checkInMetricValues(for: .evening)
            ) { input in
                state.upsertCheckIn(
                    period: .evening,
                    energy: input.energy,
                    mood: input.mood,
                    stress: input.stress,
                    note: input.note,
                    customMetricValues: input.metricValues
                )
            }.withSheetDetentsIfAvailable()
        }
        .sheet(isPresented: $showingCheckInMetricSelection) {
            NavigationView {
                CheckInMetricSelectionView(selectionRaw: $selectedCheckInMetricIdsRaw)
                    .environmentObject(state)
            }
            .withSheetDetentsIfAvailable()
        }
        .sheet(item: $selectedProtocol) { item in
            ProtocolLogSheet(item: item) { completed, note in
                if completed {
                    let completion = ProtocolCompletion(protocolId: item.id, date: Date(), completed: true)
                    state.protocolCompletions.append(completion)
                    state.save()
                }
            }.withSheetDetentsIfAvailable()
        }
        .sheet(item: $selectedSupplement) { sup in
            SupplementLogSheet(supplement: sup) { taken, note in
                if taken {
                    let intake = SupplementIntake(supplementId: sup.id, date: Date(), taken: true)
                    state.supplementIntakes.append(intake)
                    state.save()
                }
            }.withSheetDetentsIfAvailable()
        }
        .sheet(isPresented: $showingReminder) {
            ReminderSheet { data in
                // Persist the reminder locally
                let comps = Calendar.current.dateComponents([.hour, .minute], from: data.time)
                let r = Reminder(title: data.title,
                                  hour: comps.hour ?? 8,
                                  minute: comps.minute ?? 0,
                                  weekdays: Array(data.days).sorted(),
                                  notes: data.description,
                                  enabled: data.notificationsEnabled)
                state.reminders.append(r)
                state.save()

                if data.notificationsEnabled {
                    NotificationService.shared.scheduleReminder(r)
                }
            }.withSheetDetentsIfAvailable()
        }
        .onAppear {
            state.applyAdaptiveGoalPolicyIfNeeded()
            state.refreshInsightsAndRecommendations()
        }
        .overlay {
            if showingStreakAchievements {
                StreakAchievementsPopup(isPresented: $showingStreakAchievements)
                    .environmentObject(state)
            }
        }
    }

    private var brandMark: some View {
        Image("HeaderLogo")
            .resizable()
            .scaledToFit()
            .frame(width: 34, height: 34)
            .frame(width: 36, height: 36, alignment: .leading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("AJUSTE")
    }

    private var header: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Votre Checklist quotidienne")
                    .font(.title3.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                    .allowsTightening(true)
                    .truncationMode(.tail)
                Text(Date(), style: .date)
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(UIColor.secondarySystemBackground))
                    .clipShape(Capsule())
            }
            Spacer()
            todayTasksButton
                .offset(x: 4, y: -8)
        }
    }

    private var todayTasksButton: some View {
        let pendingTasks = todayTaskCount()
        return Button(action: {
            withAnimation(.spring(response: 0.28, dampingFraction: 0.88)) {
                showingObjectives = true
            }
        }) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color("Primary"), Color("Primary").opacity(0.65)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 44, height: 44)
                    .overlay(
                        Circle()
                            .stroke(Color.white.opacity(0.45), lineWidth: 1)
                    )
                    .shadow(color: Color("Primary").opacity(0.35), radius: 8, x: 0, y: 4)
                Text(todayTasksBadgeLabel(for: pendingTasks))
                    .font(.system(size: 17, weight: .heavy, design: .rounded))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button {
                showingStreakAchievements = true
            } label: {
                Label("Voir les succès", systemImage: "trophy.fill")
            }
        }
        .accessibilityLabel("Voir les éléments à faire aujourd'hui")
        .accessibilityValue("\(pendingTasks) élément(s) restant(s)")
        .accessibilityHint("Affiche le détail de vos actions du jour")
    }

    private var checkInCard: some View {
        SurfaceCard {
            HStack {
                Text("Check-ins quotidiens").font(.headline)
                Spacer()
                Button(action: { showingCheckInMetricSelection = true }) {
                    Image(systemName: "slider.horizontal.3")
                        .imageScale(.small)
                }
                .buttonStyle(CirclePressIconButtonStyle())
                .accessibilityLabel("Personnaliser les champs de check-in")
                Text("\(checkInCompletionCount())/2")
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.secondary)
            }
            if !selectedCheckInMetrics.isEmpty {
                Text("\(selectedCheckInMetrics.count) métrique(s) personnalisée(s) depuis Suivi")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            HStack(spacing: 10) {
                checkInButton(period: .morning, isDone: state.checkIn(for: .morning) != nil) {
                    showingMorningCheckIn = true
                }
                checkInButton(period: .evening, isDone: state.checkIn(for: .evening) != nil) {
                    showingEveningCheckIn = true
                }
            }
        }
    }

    private func checkInButton(period: CheckInPeriod, isDone: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: isDone ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isDone ? .green : .secondary)
                Text(period.displayName)
                Spacer()
                Text(isDone ? "Complété" : "À faire")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(Color(UIColor.secondarySystemBackground)))
        }
        .buttonStyle(.plain)
    }

    private var recommendationsCard: some View {
        SurfaceCard {
            HStack {
                Text("Recommandations").font(.headline)
                Spacer()
                Button(action: {
                    state.refreshInsightsAndRecommendations()
                }) {
                    Image(systemName: "arrow.clockwise")
                        .imageScale(.small)
                }
                .buttonStyle(CirclePressIconButtonStyle())
                .accessibilityLabel("Rafraîchir les recommandations")

                Button(action: {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        collapseRecommendationsCard.toggle()
                    }
                }) {
                    Image(systemName: collapseRecommendationsCard ? "chevron.down" : "chevron.up")
                        .imageScale(.small)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel(collapseRecommendationsCard ? "Déplier les recommandations" : "Replier les recommandations")
            }

            if collapseRecommendationsCard {
                HStack(spacing: 8) {
                    Image(systemName: state.recommendations.isEmpty ? "sparkles" : "lightbulb.max.fill")
                        .foregroundColor(Color("Primary"))
                    Text(state.recommendations.isEmpty ? "Aucune recommandation en attente" : "\(min(3, state.recommendations.count)) recommandation(s) disponible(s)")
                        .foregroundColor(.secondary)
                        .font(.caption)
                }
            } else {
                if state.recommendations.isEmpty {
                    Text("Aucune recommandation pour le moment.")
                        .foregroundColor(.secondary)
                        .font(.caption)
                } else {
                    ForEach(Array(state.recommendations.prefix(3))) { item in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.subheadline.weight(.semibold))
                            Text(item.message)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, 4)
                    }
                }
            }
        }
    }

    private var remindersCard: some View {
        SurfaceCard {
            HStack {
                Text("Rappels pour aujourd'hui").font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                Spacer()
                Button("Gérer") { showingManageReminders = true }
            }
            VStack(spacing: 8) {
                if state.reminders.isEmpty {
                    VStack(spacing: 8) {
                        Image(systemName: "bell").font(.title3).foregroundColor(.secondary)
                        Text("Aucun rappel").foregroundColor(.secondary)
                        Button("Ajouter un rappel") { showingReminder = true }
                            .buttonStyle(.bordered)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 120)
                } else {
                    ForEach(sortedReminders.prefix(3)) { r in
                        HStack {
                            Image(systemName: r.enabled ? "bell.fill" : "bell.slash")
                                .foregroundColor(r.enabled ? Color("Primary") : .secondary)
                                .imageScale(.small)
                            Text(r.title)
                            Spacer()
                            Text(String(format: "%02d:%02d", r.hour, r.minute))
                                .foregroundColor(.secondary)
                            Toggle("", isOn: Binding(
                                get: { r.enabled },
                                set: { newValue in
                                    if let idx = state.reminders.firstIndex(where: { $0.id == r.id }) {
                                        state.reminders[idx].enabled = newValue
                                        let updated = state.reminders[idx]
                                        if newValue {
                                            NotificationService.shared.scheduleReminder(updated)
                                        } else {
                                            NotificationService.shared.cancelReminder(baseId: updated.notificationBaseId)
                                        }
                                        state.save()
                                    }
                                }
                            ))
                            .labelsHidden()
                        }
                    }
                }
            }
        }
    }
    @State private var showingReminder = false
    @State private var showingManageReminders = false

    // MARK: - Objectifs (Progress Card)
    private var objectivesCard: some View {
        let total = objectivesTotalToday()
        let done = objectivesDoneToday()
        let progress = total == 0 ? 0.0 : Double(done) / Double(total)
        return SurfaceCard {
            HStack {
                Text("Objectifs").font(.headline)
                Spacer()
            }
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(LinearGradient(
                        colors: [Color("Primary").opacity(0.25), Color("Primary").opacity(0.15)],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    ))
                    .frame(height: 80)
                GeometryReader { geo in
                    let width = geo.size.width
                    let fillWidth = CGFloat(progress) * width
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(LinearGradient(
                            colors: [Color("Primary"), Color("Primary").opacity(0.7)],
                            startPoint: .leading, endPoint: .trailing
                        ))
                        .frame(width: max(0, fillWidth), height: 80)
                        .opacity(progress <= 0 ? 0 : 1)
                        .animation(.easeInOut(duration: 0.25), value: progress)

                    // Infos centrées dans la jauge (texte seul)
                    VStack(spacing: 2) {
                        Text(String(format: "%.0f%%", progress * 100))
                            .font(.headline.weight(.bold))
                            .foregroundColor(.primary)
                            .shadow(color: .black.opacity(0.1), radius: 1, x: 0, y: 0)
                        Text("\(done)/\(total) objectifs réalisés")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(.primary.opacity(0.9))
                            .shadow(color: .black.opacity(0.08), radius: 1, x: 0, y: 0)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .frame(height: 80)
            }
            // Ligne d’info enlevée car désormais affichée dans la barre
        }
        .contentShape(Rectangle())
        .onTapGesture { showingObjectives = true }
        .sheet(isPresented: $showingObjectives) {
            ObjectivesDetailPopup()
                .environmentObject(state)
        }
    }

    private var protocolsSection: some View {
        SurfaceCard {
            HStack {
                Text("Protocoles à suivre aujourd'hui").font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                    .contentShape(Rectangle())
                    .onTapGesture { selectedTab = 3 }
                Spacer()
                Button("Voir tous") { selectedTab = 3 }
            }
            VStack(spacing: 12) {
                ForEach(protocolsScheduledToday().prefix(3)) { p in
                    HStack(spacing: 12) {
                        checkBox(isOn: isProtocolDoneToday(p)) {
                            toggleProtocol(p)
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(p.name)
                                .font(.headline)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                                .overlay(
                                    Group { if isProtocolDoneToday(p) { Rectangle().fill(Color.clear) } }
                                )
                                .modifier(StrikethroughCompat(active: isProtocolDoneToday(p)))
                            Text(label(for: p.frequency))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                                .contentShape(Rectangle())
                                .onTapGesture { selectedTab = 3 }
                        }
                        Spacer()
                        HStack(spacing: 8) {
                            Button {
                                toggleProtocolTimer(p)
                            } label: {
                                Image(systemName: ProtocolLiveActivityService.shared.activeProtocolId() == p.id ? "stop.circle" : "timer")
                                    .foregroundColor(Color("Primary"))
                            }
                            Button { selectedProtocol = p } label: { Image(systemName: "chevron.right").foregroundColor(.secondary) }
                        }
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(UIColor.secondarySystemBackground)))
                    .contentShape(Rectangle())
                    .onTapGesture { selectedTab = 3 }
                }
                progressRow(done: protocolsDoneCount(), total: max(1, protocolsScheduledToday().count))
            }
        }
        .sheet(isPresented: $showingManageReminders) { ManageRemindersSheet().environmentObject(state) }
    }

    private var supplementsSection: some View {
        SurfaceCard {
            HStack {
                Text("Compléments à prendre aujourd'hui").font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.9)
                    .truncationMode(.tail)
                    .contentShape(Rectangle())
                    .onTapGesture { selectedTab = 4 }
                Spacer()
                Button("Voir tous") { selectedTab = 4 }
            }
            VStack(spacing: 12) {
                ForEach(supplementsScheduledToday().prefix(5)) { s in
                    HStack(spacing: 12) {
                        checkBox(isOn: isSupplementTakenToday(s)) {
                            toggleSupplement(s)
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 8) {
                                if let cat = s.category {
                                    Image(systemName: categoryIconName(for: cat))
                                        .imageScale(.medium)
                                        .foregroundColor(categoryColor(for: cat))
                                }
                                Text(s.name)
                                    .font(.headline)
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.85)
                                    .modifier(StrikethroughCompat(active: isSupplementTakenToday(s)))
                            }
                            HStack(spacing: 6) {
                                if let dose = s.dose { Text(dose) }
                                if let t = s.timeContext ?? (s.timeOfDay != nil ? "matin" : nil) { Text("• \(t)") }
                            }
                            .foregroundColor(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .contentShape(Rectangle())
                            .onTapGesture { selectedTab = 4 }
                        }
                        Spacer()
                        Button { selectedSupplement = s } label: { Image(systemName: "chevron.right").foregroundColor(.secondary) }
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(UIColor.secondarySystemBackground)))
                    .contentShape(Rectangle())
                    .onTapGesture { selectedTab = 4 }
                }
                progressRow(done: supplementsDoneCount(), total: max(1, supplementsScheduledToday().count))
            }
        }
    }

    private func progressRow(done: Int, total: Int) -> some View {
        HStack(spacing: 8) {
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(Color(UIColor.secondarySystemFill))
                    .frame(height: 4)
                GeometryReader { geo in
                    let ratio = total == 0 ? 0.0 : min(1.0, Double(done) / Double(total))
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(Color("Primary"))
                        .frame(width: CGFloat(ratio) * geo.size.width, height: 4)
                        .animation(.easeInOut(duration: 0.25), value: ratio)
                }
            }
            .frame(height: 4)
            .frame(maxWidth: .infinity)

            Text("\(done) sur \(total) complété")
                .foregroundColor(.secondary)
                .font(.caption)
        }
    }

    private func label(for frequency: Frequency) -> String {
        switch frequency {
        case .daily: return "Quotidien"
        case .weekly(let days):
            let map = [1:"Lun",2:"Mar",3:"Mer",4:"Jeu",5:"Ven",6:"Sam",7:"Dim"]
            return days.map { map[$0] ?? "" }.joined(separator: ", ")
        case .timesPerDay(let n): return n <= 1 ? "Quotidien" : "\(n)x / jour"
        }
    }

    // MARK: - Checkboxes and state
    private func checkBox(isOn: Bool, toggle: @escaping () -> Void) -> some View {
        Button(action: toggle) {
            ZStack {
                RoundedRectangle(cornerRadius: 6, style: .continuous)
                    .stroke(isOn ? Color("Primary") : Color(UIColor.separator), lineWidth: 2)
                    .background(isOn ? Color("Primary").opacity(0.15) : Color.clear)
                    .frame(width: 24, height: 24)
                if isOn { Image(systemName: "checkmark").font(.footnote).foregroundColor(Color("Primary")) }
            }
        }
        .buttonStyle(.plain)
    }

    private func isProtocolDoneToday(_ item: ProtocolItem) -> Bool {
        state.protocolCompletions.contains { $0.protocolId == item.id && Calendar.current.isDateInToday($0.date) && $0.completed }
    }

    private func isSupplementTakenToday(_ s: Supplement) -> Bool {
        state.supplementIntakes.contains { $0.supplementId == s.id && Calendar.current.isDateInToday($0.date) && $0.taken }
    }

    private func toggleProtocol(_ item: ProtocolItem) {
        if let idx = state.protocolCompletions.firstIndex(where: { $0.protocolId == item.id && Calendar.current.isDateInToday($0.date) }) {
            state.protocolCompletions.remove(at: idx)
        } else {
            let completion = ProtocolCompletion(protocolId: item.id, date: Date(), completed: true)
            state.protocolCompletions.append(completion)
        }
        state.save()
    }

    private func toggleProtocolTimer(_ item: ProtocolItem) {
        let active = ProtocolLiveActivityService.shared.activeProtocolId()
        if active == item.id {
            ProtocolLiveActivityService.shared.stop()
        } else {
            let duration = item.targetMinutes ?? 10
            ProtocolLiveActivityService.shared.start(protocolId: item.id, protocolName: item.name, durationMinutes: duration)
        }
    }

    private func toggleSupplement(_ s: Supplement) {
        if let idx = state.supplementIntakes.firstIndex(where: { $0.supplementId == s.id && Calendar.current.isDateInToday($0.date) }) {
            state.supplementIntakes.remove(at: idx)
        } else {
            let intake = SupplementIntake(supplementId: s.id, date: Date(), taken: true)
            state.supplementIntakes.append(intake)
        }
        state.save()
    }

    private func protocolsDoneCount() -> Int { protocolsScheduledToday().filter { isProtocolDoneToday($0) }.count }
    private func supplementsDoneCount() -> Int { supplementsScheduledToday().filter { isSupplementTakenToday($0) }.count }

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

    private var sortedReminders: [Reminder] {
        let profile = state.currentRoutineProfile()
        let excluded = Set(profile?.disabledReminderIds ?? [])
        return state.reminders
            .filter { !excluded.contains($0.id) }
            .filter { DailyPlanner.isReminderScheduledToday($0, now: Date()) }
            .sorted { a, b in
            if a.hour == b.hour { return a.minute < b.minute }
            return a.hour < b.hour
        }
    }

    private func objectivesTotalToday() -> Int {
        let protocolsCount = protocolsScheduledToday().count
        let supplementsCount = supplementsScheduledToday().count
        return protocolsCount + supplementsCount
    }

    private func objectivesDoneToday() -> Int {
        let protocolsDone = protocolsDoneCount()
        let supplementsDone = supplementsDoneCount()
        return protocolsDone + supplementsDone
    }

    private func checkInCompletionCount() -> Int {
        var count = 0
        if state.checkIn(for: .morning) != nil { count += 1 }
        if state.checkIn(for: .evening) != nil { count += 1 }
        return count
    }

    private var pendingCheckInPeriods: [CheckInPeriod] {
        CheckInPeriod.allCases.filter { state.checkIn(for: $0) == nil }
    }

    private func pendingProtocolsToday() -> [ProtocolItem] {
        protocolsScheduledToday().filter { !isProtocolDoneToday($0) }
    }

    private func pendingSupplementsToday() -> [Supplement] {
        supplementsScheduledToday().filter { !isSupplementTakenToday($0) }
    }

    private func todayTaskCount() -> Int {
        pendingCheckInPeriods.count + pendingProtocolsToday().count + pendingSupplementsToday().count
    }

    private func todayTasksBadgeLabel(for count: Int) -> String {
        count > 99 ? "99+" : "\(count)"
    }

    private var selectedCheckInMetrics: [Metric] {
        let orderedIds = CheckInMetricSelection.sanitizeOrdered(
            CheckInMetricSelection.parseOrdered(selectedCheckInMetricIdsRaw),
            availableIds: state.metrics.map(\.id)
        )
        guard !orderedIds.isEmpty else { return [] }
        let byId = Dictionary(uniqueKeysWithValues: state.metrics.map { ($0.id, $0) })
        return orderedIds.compactMap { byId[$0] }
    }

    private func checkInMetricValues(for period: CheckInPeriod, on date: Date = Date()) -> [UUID: Double] {
        let calendar = Calendar.current
        let tag = "checkin:\(period.rawValue)"
        var values: [UUID: Double] = [:]

        for metric in selectedCheckInMetrics {
            let sameDayEntries = state.metricEntries.filter {
                $0.metricId == metric.id && calendar.isDate($0.date, inSameDayAs: date)
            }
            if let tagged = sameDayEntries
                .filter({ $0.notes?.hasPrefix(tag) == true })
                .sorted(by: { $0.date > $1.date })
                .first {
                values[metric.id] = tagged.value
                continue
            }
            if let latest = sameDayEntries.sorted(by: { $0.date > $1.date }).first {
                values[metric.id] = latest.value
            }
        }

        return values
    }

    private func currentWeekdayMon1ToSun7() -> Int {
        // Calendar .weekday: 1=Sun ... 7=Sat → convert to Mon=1 ... Sun=7
        let wd = Calendar.current.component(.weekday, from: Date())
        return ((wd + 5) % 7) + 1
    }

    private func isScheduledToday(_ frequency: Frequency, daysFallback: [Int]?) -> Bool {
        switch frequency {
        case .daily: return true
        case .timesPerDay: return true
        case .weekly(let days):
            let set = Set((!days.isEmpty ? days : (daysFallback ?? [])).map { $0 })
            if set.isEmpty { return true }
            return set.contains(currentWeekdayMon1ToSun7())
        }
    }

    private func protocolsScheduledToday() -> [ProtocolItem] {
        let profile = state.currentRoutineProfile()
        let base = state.protocols.filter { item in
            guard item.isActive(on: Date()) else { return false }
            switch item.frequency {
            case .daily: return true
            case .timesPerDay: return true
            case .weekly(let days):
                let set = Set(days)
                if set.isEmpty { return true }
                return set.contains(currentWeekdayMon1ToSun7())
            }
        }
        return DailyPlanner.applyProfileFilters(to: base, profile: profile)
    }

    private func supplementsScheduledToday() -> [Supplement] {
        let profile = state.currentRoutineProfile()
        let base = state.supplements.filter { s in
            guard s.isActive(on: Date()) else { return false }
            return isScheduledToday(s.frequency, daysFallback: s.daysOfWeek)
        }
        return DailyPlanner.applyProfileFilters(to: base, profile: profile)
    }
}

private struct CirclePressIconButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(8)
            .contentShape(Circle())
            .background(
                Circle()
                    .fill(Color("Primary").opacity(configuration.isPressed ? 0.18 : 0))
            )
            .animation(.easeInOut(duration: 0.12), value: configuration.isPressed)
    }
}

private struct StreakAchievementsPopup: View {
    @EnvironmentObject var state: AppState
    @Binding var isPresented: Bool

    var body: some View {
        let snapshot = state.buildSnapshot()
        let summary = StreakEngine.buildSummary(snapshot: snapshot)
        let global = summary.global
        let bestProtocol = bestProtocolLine(from: summary)
        let bestSupplement = bestSupplementLine(from: summary)
        let todaySnapshot = DailyPlanner.buildWidgetSnapshot(from: snapshot, now: Date())
        let todayDone = todaySnapshot.progressDone
        let todayTotal = todaySnapshot.progressTotal
        let completionRate = todayTotal == 0 ? 0 : Int((Double(todayDone) / Double(todayTotal)) * 100)

        BioTrackPopupBackdrop(onDismiss: {
            withAnimation(.easeInOut(duration: 0.2)) {
                isPresented = false
            }
        }) {
            BioTrackPopupCard {
                VStack(spacing: 0) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 24, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [Color("Primary"), Color("Primary").opacity(0.62)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )

                    VStack(alignment: .leading, spacing: 14) {
                        HStack {
                            Label("Hall des succès", systemImage: "trophy.fill")
                                .font(.headline)
                                .foregroundColor(Color("OnPrimary"))
                            Spacer()
                            BioTrackModalCloseButton(
                                action: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        isPresented = false
                                    }
                                },
                                foregroundColor: Color("OnPrimary"),
                                backgroundColor: Color("Primary").opacity(0.28)
                            )
                        }

                        HStack(alignment: .bottom) {
                            VStack(alignment: .leading, spacing: 0) {
                                Text("\(global)")
                                    .font(.system(size: 46, weight: .black, design: .rounded))
                                    .foregroundColor(Color("OnPrimary"))
                                Text(global > 1 ? "jours consécutifs" : "jour consécutif")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(Color("OnPrimary").opacity(0.92))
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 4) {
                                Text(streakTierTitle(for: global))
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(Color("OnPrimary"))
                                Text(streakTierSubtitle(for: global))
                                    .font(.caption2.weight(.medium))
                                    .foregroundColor(Color("OnPrimary").opacity(0.88))
                            }
                        }
                    }
                    .padding(18)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 170)

                VStack(spacing: 10) {
                    achievementCard(
                        title: "Meilleur protocole",
                        value: bestProtocol?.name ?? "Aucun pour l'instant",
                        trailing: bestProtocol.map { "\($0.days) jours" } ?? "—",
                        systemImage: "target",
                        tint: Color(red: 0.15, green: 0.48, blue: 0.95)
                    )
                    achievementCard(
                        title: "Meilleur complément",
                        value: bestSupplement?.name ?? "Aucun pour l'instant",
                        trailing: bestSupplement.map { "\($0.days) jours" } ?? "—",
                        systemImage: "pills.fill",
                        tint: Color(red: 0.14, green: 0.62, blue: 0.42)
                    )
                    achievementCard(
                        title: "Objectifs aujourd'hui",
                        value: "\(todayDone) sur \(todayTotal) complété(s)",
                        trailing: "\(completionRate)%",
                        systemImage: "checkmark.seal.fill",
                        tint: Color(red: 0.96, green: 0.52, blue: 0.15)
                    )
                }
                    .padding(16)
                }
            }
            .transition(.scale(scale: 0.95).combined(with: .opacity))
        }
    }

    private func bestProtocolLine(from summary: StreakSummary) -> (name: String, days: Int)? {
        guard let best = summary.protocolStreaks.max(by: { $0.value < $1.value }), best.value > 0 else {
            return nil
        }
        guard let name = state.protocols.first(where: { $0.id == best.key })?.name else {
            return nil
        }
        return (name, best.value)
    }

    private func bestSupplementLine(from summary: StreakSummary) -> (name: String, days: Int)? {
        guard let best = summary.supplementStreaks.max(by: { $0.value < $1.value }), best.value > 0 else {
            return nil
        }
        guard let name = state.supplements.first(where: { $0.id == best.key })?.name else {
            return nil
        }
        return (name, best.value)
    }

    private func streakTierTitle(for days: Int) -> String {
        switch days {
        case 0: return "Niveau Départ"
        case 1..<7: return "Niveau Focus"
        case 7..<21: return "Niveau Régulier"
        case 21..<45: return "Niveau Elite"
        default: return "Niveau Légende"
        }
    }

    private func streakTierSubtitle(for days: Int) -> String {
        switch days {
        case 0: return "Lance ta première série"
        case 1..<7: return "Continue chaque jour"
        case 7..<21: return "Habitude bien installée"
        case 21..<45: return "Exécution très solide"
        default: return "Constance exceptionnelle"
        }
    }

    @ViewBuilder
    private func achievementCard(title: String, value: String, trailing: String, systemImage: String, tint: Color) -> some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(tint.opacity(0.14))
                    .frame(width: 34, height: 34)
                Image(systemName: systemImage)
                    .frame(width: 20)
                    .foregroundColor(tint)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(value)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
            }
            Spacer()
            Text(trailing)
                .font(.caption.weight(.bold))
                .foregroundColor(tint)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Capsule().fill(tint.opacity(0.12)))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(UIColor.secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(tint.opacity(0.16), lineWidth: 0.6)
        )
    }
}

#Preview {
    HomeView().environmentObject(AppState())
}
