import SwiftUI

struct ReminderSheet: View {
    @Environment(\.dismiss) private var dismiss
    let initialData: ReminderData?
    let onSave: (ReminderData) -> Void

    struct ReminderData {
        var title: String
        var time: Date
        var days: Set<Int> // 1..7 (Sun=1 per Calendar)
        var description: String
        var notificationsEnabled: Bool
    }

    @State private var title: String = ""
    @State private var time: Date = Calendar.current.date(bySettingHour: 8, minute: 0, second: 0, of: Date()) ?? Date()
    @State private var days: Set<Int> = [2,3,4,5,6] // Mon..Fri
    @State private var descriptionText: String = ""
    @State private var notificationsEnabled: Bool = true
    @State private var didApplyInitialData: Bool = false

    init(initialData: ReminderData? = nil, onSave: @escaping (ReminderData) -> Void) {
        self.initialData = initialData
        self.onSave = onSave
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            Form {
                Section(header: RequiredHeader("Titre")) {
                    TextField("Saisir le titre du rappel", text: $title)
                }
                Section(header: RequiredHeader("Heure")) {
                    DatePicker("", selection: $time, displayedComponents: .hourAndMinute).labelsHidden()
                }
                Section(header: RequiredHeader("Répéter")) {
                    weekGrid
                    quickButtons
                }
                Section(header: Text("Description (optionnel)")) {
                    TextEditor(text: $descriptionText).frame(minHeight: 80)
                }
                Section(header: Text("Activer les notifications")) {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("", isOn: $notificationsEnabled).labelsHidden()
                        NotificationWarningView()
                    }
                }
            }
        }
        .onAppear {
            guard !didApplyInitialData else { return }
            didApplyInitialData = true
            guard let initialData else { return }
            title = initialData.title
            time = initialData.time
            days = initialData.days
            descriptionText = initialData.description
            notificationsEnabled = initialData.notificationsEnabled
        }
    }

    private var header: some View {
        SheetHeader(
            title: "Nouveau rappel",
            leadingTitle: "Annuler",
            onLeading: { dismiss() },
            trailingTitle: "Enregistrer",
            onTrailing: { onSave(ReminderData(title: title, time: time, days: days, description: descriptionText, notificationsEnabled: notificationsEnabled)); dismiss() },
            trailingDisabled: title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        )
    }

    private var weekGrid: some View {
        let labels = [1:"Dim",2:"Lun",3:"Mar",4:"Mer",5:"Jeu",6:"Ven",7:"Sam"]
        return HStack(spacing: 8) {
            ForEach(1...7, id: \.self) { d in
                let isSel = days.contains(d)
                Button(action: { if isSel { days.remove(d) } else { days.insert(d) } }) {
                    Text(labels[d] ?? "")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(isSel ? Color("Primary") : Color(UIColor.secondarySystemBackground))
                        .foregroundColor(isSel ? Color("OnPrimary") : .primary)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var quickButtons: some View {
        HStack {
            Spacer()
            HStack(spacing: 8) {
                Button("Semaine") { days = [2,3,4,5,6] }
                    .font(.caption)
                    .buttonStyle(.bordered)
                Button("Weekend") { days = [1,7] }
                    .font(.caption)
                    .buttonStyle(.bordered)
                Button("Tous les jours") { days = Set(1...7) }
                    .font(.caption)
                    .buttonStyle(.bordered)
            }
        }
    }
}

private struct NotificationWarningView: View {
    @State private var status: UNAuthorizationStatus = .notDetermined

    var body: some View {
        Group {
            if status == .denied || status == .notDetermined {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Les notifications ne sont pas activées pour AJUSTE.")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                    Button(status == .notDetermined ? "Autoriser les notifications" : "Activer dans Réglages") {
                        if status == .notDetermined {
                            Task {
                                _ = await NotificationService.shared.requestPermission()
                                refreshStatus()
                            }
                        } else {
                            NotificationService.shared.openSettings()
                        }
                    }
                        .font(.footnote)
                }
            }
        }
        .onAppear { refreshStatus() }
    }

    private func refreshStatus() {
        NotificationService.shared.getAuthorizationStatus { current in
            status = current
        }
    }
}
