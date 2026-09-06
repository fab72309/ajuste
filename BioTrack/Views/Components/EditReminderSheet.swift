import SwiftUI

struct EditReminderSheet: View {
    @Environment(\.dismiss) private var dismiss
    let reminder: Reminder
    let onSave: (Reminder) -> Void

    @State private var title: String
    @State private var time: Date
    @State private var days: Set<Int>
    @State private var descriptionText: String
    @State private var notificationsEnabled: Bool

    init(reminder: Reminder, onSave: @escaping (Reminder) -> Void) {
        self.reminder = reminder
        self.onSave = onSave
        _title = State(initialValue: reminder.title)
        let comps = DateComponents(calendar: Calendar.current, hour: reminder.hour, minute: reminder.minute)
        _time = State(initialValue: Calendar.current.date(from: comps) ?? Date())
        _days = State(initialValue: Set(reminder.weekdays))
        _descriptionText = State(initialValue: reminder.notes ?? "")
        _notificationsEnabled = State(initialValue: reminder.enabled)
    }

    var body: some View {
        ReminderSheetContent(title: $title,
                             time: $time,
                             days: $days,
                             descriptionText: $descriptionText,
                             notificationsEnabled: $notificationsEnabled,
                             headerTitle: "Modifier le rappel",
                             onCancel: { dismiss() },
                             onCommit: save)
    }

    private func save() {
        let comps = Calendar.current.dateComponents([.hour, .minute], from: time)
        var r = reminder
        r.title = title
        r.hour = comps.hour ?? reminder.hour
        r.minute = comps.minute ?? reminder.minute
        r.weekdays = Array(days).sorted()
        r.notes = descriptionText.isEmpty ? nil : descriptionText
        r.enabled = notificationsEnabled
        onSave(r)
        dismiss()
    }
}

// Extracted UI so we can reuse for View/Edit
private struct ReminderSheetContent: View {
    @Binding var title: String
    @Binding var time: Date
    @Binding var days: Set<Int>
    @Binding var descriptionText: String
    @Binding var notificationsEnabled: Bool
    let headerTitle: String
    let onCancel: () -> Void
    let onCommit: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            SheetHeader(
                title: headerTitle,
                leadingTitle: "Annuler",
                onLeading: { onCancel() },
                trailingTitle: "Enregistrer",
                onTrailing: { onCommit() },
                trailingDisabled: title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            )

            Form {
                Section(header: RequiredHeader("Titre")) {
                    TextField("Saisir le titre du rappel", text: $title)
                }
                Section(header: RequiredHeader("Heure")) {
                    DatePicker("", selection: $time, displayedComponents: .hourAndMinute).labelsHidden()
                }
                Section(header: RequiredHeader("Répéter")) {
                    WeekGrid(days: $days)
                    QuickButtons(days: $days)
                }
                Section(header: Text("Description (optionnel)")) {
                    TextEditor(text: $descriptionText).frame(minHeight: 80)
                }
                Section(header: Text("Activer les notifications")) {
                    VStack(alignment: .leading, spacing: 8) {
                        Toggle("", isOn: $notificationsEnabled).labelsHidden()
                        // Alerte autorisation (réutiliser contenu simple sans dépendre de la vue d'origine)
                        Text("Assurez-vous que les notifications iOS sont activées pour AJUSTE.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }
}

private struct WeekGrid: View {
    @Binding var days: Set<Int>
    var body: some View {
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
}

private struct QuickButtons: View {
    @Binding var days: Set<Int>
    var body: some View {
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

