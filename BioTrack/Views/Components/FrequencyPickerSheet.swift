import SwiftUI

struct FrequencyPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let current: Frequency
    let onDone: (Frequency) -> Void

    @State private var working: Frequency = .daily

    var body: some View {
        VStack(spacing: 0) {
            header
            Divider()
            List {
                selectableRow(title: "Quotidienne", selected: isSelected(.daily)) { working = .daily }
                selectableRow(title: "Plusieurs fois par jour", selected: isTimesPerDay) { if !isTimesPerDay { working = .timesPerDay(2) } }
                selectableRow(title: "Si besoin", selected: working.isAsNeeded) { working = .weekly(days: []) }
                if case .timesPerDay(let n) = working {
                    Stepper(value: Binding(get: { n }, set: { working = .timesPerDay(max(1, $0)) }), in: 1...12) {
                        Text("Prises par jour: \(n)")
                    }
                }
                selectableRow(title: "Jour(s) spécifique(s)", selected: working.hasSpecificDays) { if !working.hasSpecificDays { working = .weekly(days: [2]) } }
                if case .weekly(let days) = working, !days.isEmpty {
                    WeekdayPicker(selection: Binding(get: { Set(days) }, set: { working = .weekly(days: Array($0).sorted()) }))
                        .padding(.vertical, 4)
                }
            }
        }
        .onAppear { working = current }
    }

    private var isTimesPerDay: Bool {
        if case .timesPerDay = working { return true }
        return false
    }

    private func isSelected(_ candidate: Frequency) -> Bool { working == candidate }

    private var header: some View {
        HStack {
            Button("Annuler") { dismiss() }
            Spacer()
            Text("Sélectionner la fréquence").font(.headline)
            Spacer()
            Button("Terminé") { onDone(working); dismiss() }
                .font(.headline)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.ultraThinMaterial)
    }

    private func selectableRow(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundColor(.accentColor) }
            }
        }
    }
}

struct WeekdayPicker: View {
    @Binding var selection: Set<Int> // 1=Lun ... 7=Dim

    var body: some View {
        let labels = [1:"Lun",2:"Mar",3:"Mer",4:"Jeu",5:"Ven",6:"Sam",7:"Dim"]
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(1...7, id: \.self) { d in
                    let isSel = selection.contains(d)
                    Button(action: {
                        if isSel { selection.remove(d) } else { selection.insert(d) }
                    }) {
                        Text(labels[d] ?? "")
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 10)
                            .background(isSel ? Color("Primary") : Color(UIColor.secondarySystemBackground))
                            .foregroundColor(isSel ? Color("OnPrimary") : .primary)
                            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .contentShape(Rectangle())
                }
            }
        }
        .padding(.vertical, 4)
    }
}

