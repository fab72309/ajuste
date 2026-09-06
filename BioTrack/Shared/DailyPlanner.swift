import Foundation

enum DailyPlanner {
    static func buildWidgetSnapshot(from snapshot: BioTrackSnapshot, now: Date = Date()) -> WidgetDailySnapshot {
        let profile = activeProfile(snapshot: snapshot, now: now)
        let protocolsToday = applyProfileFilters(to: protocolsScheduledToday(from: snapshot, now: now), profile: profile)
        let supplementsToday = applyProfileFilters(to: supplementsScheduledToday(from: snapshot, now: now), profile: profile)

        let priorityProtocolItems = protocolsToday.map { protocolItem -> WidgetPriorityItem in
            WidgetPriorityItem(
                itemId: protocolItem.id,
                kind: .protocolItem,
                title: protocolItem.name,
                isDone: isProtocolDoneToday(protocolItem.id, snapshot: snapshot, now: now),
                subtitle: label(for: protocolItem.frequency),
                preferredMinutes: minutesOfDay(from: protocolItem.preferredHour)
            )
        }

        let prioritySupplementItems = supplementsToday.map { supplement -> WidgetPriorityItem in
            let subtitle: String?
            if let dose = supplement.dose {
                subtitle = dose
            } else if let context = supplement.timeContext {
                subtitle = context
            } else {
                subtitle = nil
            }
            return WidgetPriorityItem(
                itemId: supplement.id,
                kind: .supplement,
                title: supplement.name,
                isDone: isSupplementTakenToday(supplement.id, snapshot: snapshot, now: now),
                subtitle: subtitle,
                preferredMinutes: minutesOfDay(from: supplement.timeOfDay)
            )
        }

        let allItems = (priorityProtocolItems + prioritySupplementItems).sorted { lhs, rhs in
            if lhs.isDone != rhs.isDone { return !lhs.isDone }
            let leftDistance = preferredDistance(lhs.preferredMinutes, now: now)
            let rightDistance = preferredDistance(rhs.preferredMinutes, now: now)
            if leftDistance != rightDistance { return leftDistance < rightDistance }
            return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
        }

        let reminders = applyProfileFilters(to: upcomingRemindersToday(from: snapshot, now: now), profile: profile)
        let reminderItems = reminders.map {
            WidgetReminderItem(reminderId: $0.id, title: $0.title, hour: $0.hour, minute: $0.minute, enabled: $0.enabled)
        }

        let doneCount = allItems.filter(\.isDone).count
        let recommendations = snapshot.recommendations.prefix(2).map(\.title)
        return WidgetDailySnapshot(
            date: now,
            progressDone: doneCount,
            progressTotal: allItems.count,
            priorityItems: allItems,
            upcomingReminders: reminderItems,
            routineProfileName: profile?.name,
            recommendations: recommendations
        )
    }

    static func activeProfile(snapshot: BioTrackSnapshot, now: Date = Date()) -> RoutineProfile? {
        let kind: RoutineProfileKind
        if let raw = snapshot.activeRoutineProfileKindRaw,
           let explicit = RoutineProfileKind(rawValue: raw) {
            kind = explicit
        } else {
            let weekday = currentWeekdayMon1ToSun7(now: now)
            kind = (weekday == 6 || weekday == 7) ? .weekend : .weekday
        }
        return snapshot.routineProfiles.first(where: { $0.kind == kind })
    }

    static func applyProfileFilters(to protocols: [ProtocolItem], profile: RoutineProfile?) -> [ProtocolItem] {
        guard let profile else { return protocols }
        let excluded = Set(profile.disabledProtocolIds)
        return protocols.filter { !excluded.contains($0.id) }
    }

    static func applyProfileFilters(to supplements: [Supplement], profile: RoutineProfile?) -> [Supplement] {
        guard let profile else { return supplements }
        let excluded = Set(profile.disabledSupplementIds)
        return supplements.filter { !excluded.contains($0.id) }
    }

    static func applyProfileFilters(to reminders: [Reminder], profile: RoutineProfile?) -> [Reminder] {
        guard let profile else { return reminders }
        let excluded = Set(profile.disabledReminderIds)
        return reminders.filter { !excluded.contains($0.id) }
    }

    static func toggleProtocolCompletion(protocolId: UUID, in snapshot: inout BioTrackSnapshot, now: Date = Date()) {
        if let idx = snapshot.protocolCompletions.firstIndex(where: {
            $0.protocolId == protocolId && Calendar.current.isDate($0.date, inSameDayAs: now)
        }) {
            snapshot.protocolCompletions.remove(at: idx)
            return
        }
        snapshot.protocolCompletions.append(ProtocolCompletion(protocolId: protocolId, date: now, completed: true))
    }

    static func toggleSupplementIntake(supplementId: UUID, in snapshot: inout BioTrackSnapshot, now: Date = Date()) {
        if let idx = snapshot.supplementIntakes.firstIndex(where: {
            $0.supplementId == supplementId && Calendar.current.isDate($0.date, inSameDayAs: now)
        }) {
            snapshot.supplementIntakes.remove(at: idx)
            return
        }
        snapshot.supplementIntakes.append(SupplementIntake(supplementId: supplementId, date: now, taken: true))
    }

    static func toggleReminder(reminderId: UUID, in snapshot: inout BioTrackSnapshot) {
        guard let idx = snapshot.reminders.firstIndex(where: { $0.id == reminderId }) else { return }
        snapshot.reminders[idx].enabled.toggle()
    }

    static func protocolsScheduledToday(from snapshot: BioTrackSnapshot, now: Date = Date()) -> [ProtocolItem] {
        snapshot.protocols.filter { item in
            guard item.isActive(on: now) else { return false }
            switch item.frequency {
            case .daily, .timesPerDay:
                return true
            case .weekly(let days):
                let set = Set(days)
                if set.isEmpty { return false }
                return set.contains(currentWeekdayMon1ToSun7(now: now))
            }
        }
    }

    static func supplementsScheduledToday(from snapshot: BioTrackSnapshot, now: Date = Date()) -> [Supplement] {
        snapshot.supplements.filter { supplement in
            guard supplement.isActive(on: now) else { return false }
            return isScheduledToday(supplement.frequency, daysFallback: supplement.daysOfWeek, now: now)
        }
    }

    static func upcomingRemindersToday(from snapshot: BioTrackSnapshot, now: Date = Date()) -> [Reminder] {
        let currentMinutes = minutesOfDay(from: Calendar.current.dateComponents([.hour, .minute], from: now)) ?? 0
        return snapshot.reminders
            .filter { $0.enabled && isReminderScheduledToday($0, now: now) }
            .filter { ($0.hour * 60 + $0.minute) >= currentMinutes }
            .sorted { lhs, rhs in
                let left = lhs.hour * 60 + lhs.minute
                let right = rhs.hour * 60 + rhs.minute
                if left != right { return left < right }
                return lhs.title.localizedCaseInsensitiveCompare(rhs.title) == .orderedAscending
            }
    }

    static func isProtocolDoneToday(_ protocolId: UUID, snapshot: BioTrackSnapshot, now: Date = Date()) -> Bool {
        snapshot.protocolCompletions.contains {
            $0.protocolId == protocolId && Calendar.current.isDate($0.date, inSameDayAs: now) && $0.completed
        }
    }

    static func isSupplementTakenToday(_ supplementId: UUID, snapshot: BioTrackSnapshot, now: Date = Date()) -> Bool {
        snapshot.supplementIntakes.contains {
            $0.supplementId == supplementId && Calendar.current.isDate($0.date, inSameDayAs: now) && $0.taken
        }
    }

    static func label(for frequency: Frequency) -> String {
        switch frequency {
        case .daily:
            return "Quotidien"
        case .weekly(let days):
            let map = [1: "Lun", 2: "Mar", 3: "Mer", 4: "Jeu", 5: "Ven", 6: "Sam", 7: "Dim"]
            return days.isEmpty ? "Si besoin" : days.map { map[$0] ?? "" }.joined(separator: ", ")
        case .timesPerDay(let value):
            return value <= 1 ? "Quotidien" : "\(value)x / jour"
        }
    }

    static func currentWeekdayMon1ToSun7(now: Date = Date()) -> Int {
        let weekday = Calendar.current.component(.weekday, from: now)
        return ((weekday + 5) % 7) + 1
    }

    static func isScheduledToday(_ frequency: Frequency, daysFallback: [Int]?, now: Date = Date()) -> Bool {
        switch frequency {
        case .daily, .timesPerDay:
            return true
        case .weekly(let days):
            let set = Set((!days.isEmpty ? days : (daysFallback ?? [])))
            if set.isEmpty { return false }
            return set.contains(currentWeekdayMon1ToSun7(now: now))
        }
    }

    static func isReminderScheduledToday(_ reminder: Reminder, now: Date = Date()) -> Bool {
        if reminder.weekdays.isEmpty { return true }
        let weekday = Calendar.current.component(.weekday, from: now)
        return reminder.weekdays.contains(weekday)
    }

    static func minutesOfDay(from components: DateComponents?) -> Int? {
        guard let hour = components?.hour, let minute = components?.minute else { return nil }
        return hour * 60 + minute
    }

    static func preferredDistance(_ preferredMinutes: Int?, now: Date) -> Int {
        guard let preferredMinutes else { return Int.max - 1 }
        let nowMinutes = minutesOfDay(from: Calendar.current.dateComponents([.hour, .minute], from: now)) ?? 0
        return abs(preferredMinutes - nowMinutes)
    }
}
