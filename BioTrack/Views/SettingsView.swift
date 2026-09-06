import SwiftUI
import UniformTypeIdentifiers
import UserNotifications

struct SettingsView: View {
    @EnvironmentObject var state: AppState
    @Environment(\.dismiss) private var dismiss
    @AppStorage("hasCompletedInitialOnboarding") private var hasCompletedInitialOnboarding: Bool = false
    @AppStorage("darkMode") private var darkMode: Bool = false
    @AppStorage("showRecommendationsCard") private var showRecommendationsCard: Bool = true
    @AppStorage(CheckInMetricSelection.storageKey) private var selectedCheckInMetricIdsRaw: String = ""
    @AppStorage("weightBaselineKg") private var weightBaselineKg: Double = 70
    @AppStorage("healthAutoSync") private var healthAutoSync: Bool = true
    @AppStorage("healthImportRangeDays") private var healthImportRangeDays: Int = 30
    @AppStorage("healthDedupePolicy") private var healthDedupePolicyRaw: String = HealthDedupePolicy.replace.rawValue
    @State private var showingExport = false
    @State private var exportDocument: SnapshotDocument?
    @State private var showingSecureExport = false
    @State private var secureExportDocument: SecureSnapshotDocument?
    @State private var showingImport = false
    @State private var showingSecureImport = false
    @State private var pendingSecureEnvelopeData: Data?
    @State private var showingExportPassphrase = false
    @State private var showingImportPassphrase = false
    @State private var importStatusMessage: String?
    @State private var showingImportAlert = false
    @State private var notificationStatus: UNAuthorizationStatus = .notDetermined
    @State private var activeLegalDocument: LegalDocument?

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Général")) {
                    Button(action: handleNotificationsButtonTap) {
                        HStack {
                            Image(systemName: "bell")
                            Text("Notifications")
                            Spacer()
                            Text(notificationStatusText)
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)

                    Toggle(isOn: $darkMode) {
                        HStack { Image(systemName: "moon.fill"); Text("Mode sombre") }
                    }
                }
                Section(header: Text("Routine")) {
                    NavigationLink(destination: RoutineProfilesSettingsView().environmentObject(state)) {
                        HStack {
                            Image(systemName: "calendar.badge.clock")
                            Text("Profils de routine")
                            Spacer()
                            Text(state.activeRoutineProfileKind.displayName)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                Section(header: Text("Checklist")) {
                    Toggle(isOn: $showRecommendationsCard) {
                        HStack {
                            Image(systemName: "lightbulb.max")
                            Text("Afficher la carte Recommandations")
                        }
                    }
                    NavigationLink(destination: CheckInMetricSelectionView(selectionRaw: $selectedCheckInMetricIdsRaw).environmentObject(state)) {
                        HStack {
                            Image(systemName: "slider.horizontal.3")
                            Text("Champs des check-ins")
                            Spacer()
                            Text(checkInFieldsSummary)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                Section(header: Text("Métriques")) {
                    HStack {
                        Image(systemName: "scalemass")
                        Text("Poids de référence")
                        Spacer()
                        Text(String(format: "%.1f kg", weightBaselineKg)).foregroundColor(.secondary)
                    }
                    HStack {
                        Spacer(minLength: 20)
                        Stepper("", value: $weightBaselineKg, in: 30...200, step: 0.5)
                            .labelsHidden()
                    }
                }
                Section(header: Text("Santé")) {
                    HStack {
                        Image(systemName: "heart.fill")
                        Text("Statut")
                        Spacer()
                        Text(healthStatusText).foregroundColor(.secondary)
                    }
                    if state.healthKitStatus != .authorized {
                        Button(action: {
                            Task { await state.syncHealthKit(rangeDays: healthImportRangeDays, dedupePolicy: dedupePolicy) }
                        }) {
                            HStack {
                                Image(systemName: "heart.circle")
                                Text("Connecter l’app Santé")
                                Spacer()
                                Image(systemName: "chevron.right").foregroundColor(.secondary)
                            }
                        }
                    }
                    if state.healthKitStatus == .denied {
                        Button(action: { NotificationService.shared.openSettings() }) {
                            HStack {
                                Image(systemName: "gear")
                                Text("Ouvrir Réglages iOS")
                                Spacer()
                                Image(systemName: "chevron.right").foregroundColor(.secondary)
                            }
                        }
                    }
                    Toggle(isOn: $healthAutoSync) {
                        HStack {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text("Synchronisation automatique")
                        }
                    }
                    Picker("Période d'import", selection: $healthImportRangeDays) {
                        Text("7 jours").tag(7)
                        Text("30 jours").tag(30)
                        Text("90 jours").tag(90)
                    }
                    Picker("Gestion des doublons", selection: $healthDedupePolicyRaw) {
                        ForEach(HealthDedupePolicy.allCases) { policy in
                            Text(policy.displayName).tag(policy.rawValue)
                        }
                    }
                    Button(action: { Task { await state.syncHealthKit(rangeDays: healthImportRangeDays, dedupePolicy: dedupePolicy) } }) {
                        HStack {
                            Image(systemName: "arrow.triangle.2.circlepath")
                            Text(state.isHealthKitSyncing ? "Synchronisation..." : "Synchroniser maintenant")
                            Spacer()
                            if state.isHealthKitSyncing {
                                ProgressView()
                            } else {
                                Image(systemName: "chevron.right").foregroundColor(.secondary)
                            }
                        }
                    }
                    .disabled(state.healthKitStatus == .notAvailable || state.isHealthKitSyncing)
                }
                Section(header: Text("Sauvegarde")) {
                    Button(action: beginExport) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                            Text("Exporter les données")
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                    Button(action: { showingImport = true }) {
                        HStack {
                            Image(systemName: "square.and.arrow.down")
                            Text("Importer une sauvegarde")
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                    Button(action: { showingExportPassphrase = true }) {
                        HStack {
                            Image(systemName: "lock.shield")
                            Text("Exporter chiffré")
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                    Button(action: { showingSecureImport = true }) {
                        HStack {
                            Image(systemName: "lock.open")
                            Text("Importer chiffré")
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                }
                Section(header: Text("Objectifs adaptatifs")) {
                    Toggle("Activer", isOn: Binding(
                        get: { state.adaptiveGoalPolicy.enabled },
                        set: { newValue in
                            state.adaptiveGoalPolicy.enabled = newValue
                            state.save()
                        }
                    ))
                    Stepper(value: Binding(
                        get: { state.adaptiveGoalPolicy.maxDailyTarget },
                        set: { newValue in
                            state.adaptiveGoalPolicy.maxDailyTarget = max(state.adaptiveGoalPolicy.minDailyTarget, newValue)
                            state.save()
                        }
                    ), in: 1...40) {
                        Text("Objectif max quotidien: \(state.adaptiveGoalPolicy.maxDailyTarget)")
                    }
                }
                #if DEBUG
                    Section(header: Text("Debug")) {
                        Button(action: {
                            let debug = LocalAnalyticsService.exportDebugSummary()
                            importStatusMessage = debug
                            showingImportAlert = true
                        }) {
                            HStack {
                                Image(systemName: "doc.text.magnifyingglass")
                                Text("Voir analytics locales")
                            }
                        }
                    }
                #endif
                Section(header: Text("À propos")) {
                    HStack { Text("Version"); Spacer(); Text(appVersionText).foregroundColor(.secondary) }
                }
                Section(header: Text(NSLocalizedString("settings.legal.section", comment: ""))) {
                    legalButton(
                        title: NSLocalizedString("legal.privacy.title", comment: ""),
                        systemImage: "lock.shield"
                    ) {
                        activeLegalDocument = .privacy
                    }
                    legalButton(
                        title: NSLocalizedString("legal.support.title", comment: ""),
                        systemImage: "questionmark.bubble"
                    ) {
                        activeLegalDocument = .support
                    }
                    legalButton(
                        title: NSLocalizedString("legal.terms.title", comment: ""),
                        systemImage: "doc.text"
                    ) {
                        activeLegalDocument = .terms
                    }
                    Button(action: reviewOnboarding) {
                        HStack {
                            Image(systemName: "sparkles.rectangle.stack")
                            Text(NSLocalizedString("settings.review_onboarding", comment: ""))
                            Spacer()
                            Image(systemName: "chevron.right").foregroundColor(.secondary)
                        }
                    }
                    .foregroundColor(.primary)
                }
            }
            .navigationTitle(Text("Paramètres"))
            .fileExporter(isPresented: $showingExport,
                          document: exportDocument,
                          contentType: .json,
                          defaultFilename: "ajuste-backup") { result in
                if case let .failure(error) = result {
                    importStatusMessage = "Export échoué: \(error.localizedDescription)"
                    showingImportAlert = true
                }
            }
            .fileExporter(isPresented: $showingSecureExport,
                          document: secureExportDocument,
                          contentType: .json,
                          defaultFilename: "ajuste-backup-secure") { result in
                if case let .failure(error) = result {
                    importStatusMessage = "Export chiffré échoué: \(error.localizedDescription)"
                    showingImportAlert = true
                }
            }
            .fileImporter(isPresented: $showingImport, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
                switch result {
                case .success(let urls):
                    guard let url = urls.first else { return }
                    importSnapshot(from: url)
                case .failure(let error):
                    importStatusMessage = "Import annulé: \(error.localizedDescription)"
                    showingImportAlert = true
                }
            }
            .fileImporter(isPresented: $showingSecureImport, allowedContentTypes: [.json], allowsMultipleSelection: false) { result in
                switch result {
                case .success(let urls):
                    guard let url = urls.first else { return }
                    do {
                        let didAccess = url.startAccessingSecurityScopedResource()
                        defer { if didAccess { url.stopAccessingSecurityScopedResource() } }
                        pendingSecureEnvelopeData = try Data(contentsOf: url)
                        showingImportPassphrase = true
                    } catch {
                        importStatusMessage = "Import chiffré échoué: \(error.localizedDescription)"
                        showingImportAlert = true
                    }
                case .failure(let error):
                    importStatusMessage = "Import chiffré annulé: \(error.localizedDescription)"
                    showingImportAlert = true
                }
            }
            .sheet(isPresented: $showingExportPassphrase) {
                PassphrasePromptSheet(title: "Export chiffré", actionTitle: "Exporter", onConfirm: { passphrase in
                    beginSecureExport(passphrase: passphrase)
                }, requiresConfirmation: true)
            }
            .sheet(isPresented: $showingImportPassphrase) {
                PassphrasePromptSheet(title: "Import chiffré", actionTitle: "Importer", onConfirm: { passphrase in
                    importSecureSnapshot(passphrase: passphrase)
                })
            }
            .bioTrackNotice(
                isPresented: $showingImportAlert,
                title: "Sauvegarde",
                message: importStatusMessage ?? ""
            )
            .sheet(item: $activeLegalDocument) { document in
                LegalNoticeView(document: document)
            }
        }
        .onAppear {
            state.refreshHealthKitStatus()
            refreshNotificationStatus()
        }
    }

    private var healthStatusText: String {
        state.healthKitStatus.localizedBioTrackLabel
    }

    private var dedupePolicy: HealthDedupePolicy {
        HealthDedupePolicy(rawValue: healthDedupePolicyRaw) ?? .replace
    }

    private var appVersionText: String {
        let short = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String
        switch (short, build) {
        case let (s?, b?):
            return "\(s) (\(b))"
        case let (s?, nil):
            return s
        default:
            return "—"
        }
    }

    private var checkInFieldsSummary: String {
        let selected = CheckInMetricSelection.sanitizeOrdered(
            CheckInMetricSelection.parseOrdered(selectedCheckInMetricIdsRaw),
            availableIds: state.metrics.map(\.id)
        )
        let count = selected.count
        return count == 0 ? "Aucun" : "\(count) sélectionné(s)"
    }

    private var notificationStatusText: String {
        notificationStatus.localizedBioTrackLabel
    }

    private func refreshNotificationStatus() {
        NotificationService.shared.getAuthorizationStatus { status in
            DispatchQueue.main.async {
                notificationStatus = status
            }
        }
    }

    private func handleNotificationsButtonTap() {
        if notificationStatus == .notDetermined {
            Task {
                _ = await NotificationService.shared.requestPermission()
                refreshNotificationStatus()
            }
        } else {
            NotificationService.shared.openSettings()
        }
    }

    private func beginExport() {
        let snapshot = LocalStore.shared.load()
        exportDocument = SnapshotDocument(snapshot: snapshot)
        showingExport = true
    }

    private func beginSecureExport(passphrase: String) {
        do {
            let snapshot = LocalStore.shared.load()
            let envelope = try SecureBackupService.encrypt(snapshot: snapshot, passphrase: passphrase)
            secureExportDocument = SecureSnapshotDocument(envelope: envelope)
            showingSecureExport = true
        } catch {
            importStatusMessage = "Export chiffré échoué: \(error.localizedDescription)"
            showingImportAlert = true
        }
    }

    private func importSnapshot(from url: URL) {
        let didAccess = url.startAccessingSecurityScopedResource()
        defer { if didAccess { url.stopAccessingSecurityScopedResource() } }
        do {
            let data = try Data(contentsOf: url)
            let snapshot = try JSONDecoder().decode(LocalStore.Snapshot.self, from: data)
            Task { @MainActor in
                state.applySnapshot(snapshot)
                importStatusMessage = "Import terminé."
                showingImportAlert = true
            }
        } catch {
            importStatusMessage = "Import échoué: \(error.localizedDescription)"
            showingImportAlert = true
        }
    }

    private func importSecureSnapshot(passphrase: String) {
        guard let data = pendingSecureEnvelopeData else { return }
        do {
            if let envelope = try? JSONDecoder().decode(SecureBackupEnvelope.self, from: data) {
                let snapshot = try SecureBackupService.decrypt(envelope: envelope, passphrase: passphrase)
                Task { @MainActor in
                    state.applySnapshot(snapshot)
                    importStatusMessage = "Import chiffré terminé."
                    showingImportAlert = true
                    pendingSecureEnvelopeData = nil
                }
            } else {
                // Backward compatibility: accept legacy clear snapshot in encrypted flow with warning.
                let legacy = try JSONDecoder().decode(LocalStore.Snapshot.self, from: data)
                Task { @MainActor in
                    state.applySnapshot(legacy)
                    importStatusMessage = "Import legacy non chiffré appliqué."
                    showingImportAlert = true
                    pendingSecureEnvelopeData = nil
                }
            }
        } catch {
            importStatusMessage = "Import chiffré échoué: \(error.localizedDescription)"
            showingImportAlert = true
        }
    }

    @ViewBuilder
    private func legalButton(title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage)
                Text(title)
                Spacer()
                Image(systemName: "chevron.right").foregroundColor(.secondary)
            }
        }
        .foregroundColor(.primary)
    }

    private func reviewOnboarding() {
        hasCompletedInitialOnboarding = false
        dismiss()
    }
}

#Preview { SettingsView().environmentObject(AppState()) }

private struct SnapshotDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var snapshot: LocalStore.Snapshot

    init(snapshot: LocalStore.Snapshot) {
        self.snapshot = snapshot
    }

    init(configuration: ReadConfiguration) throws {
        let data = configuration.file.regularFileContents ?? Data()
        snapshot = try JSONDecoder().decode(LocalStore.Snapshot.self, from: data)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(snapshot)
        return .init(regularFileWithContents: data)
    }
}

private struct SecureSnapshotDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var envelope: SecureBackupEnvelope

    init(envelope: SecureBackupEnvelope) {
        self.envelope = envelope
    }

    init(configuration: ReadConfiguration) throws {
        let data = configuration.file.regularFileContents ?? Data()
        envelope = try JSONDecoder().decode(SecureBackupEnvelope.self, from: data)
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(envelope)
        return .init(regularFileWithContents: data)
    }
}
