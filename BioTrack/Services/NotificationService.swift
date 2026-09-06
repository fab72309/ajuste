import Foundation
import UIKit
import UserNotifications

extension Notification.Name {
    static let bioTrackReminderMarkedDone = Notification.Name("bioTrackReminderMarkedDone")
}

enum ReminderNotificationAction: String, CaseIterable {
    case done = "REMINDER_DONE"
    case snooze15 = "REMINDER_SNOOZE_15"
    case snooze30 = "REMINDER_SNOOZE_30"
    case snooze60 = "REMINDER_SNOOZE_60"

    var minutes: Int? {
        switch self {
        case .done: return nil
        case .snooze15: return 15
        case .snooze30: return 30
        case .snooze60: return 60
        }
    }
}

final class NotificationService {
    static let shared = NotificationService()
    static let reminderBaseIdUserInfoKey = "reminderBaseId"
    private init() {}

    private let center = UNUserNotificationCenter.current()
    private let categoryId = ReminderNotificationScheduler.reminderCategoryId
    private let reminderBaseIdKey = NotificationService.reminderBaseIdUserInfoKey
    private let reminderTitleKey = "reminderTitle"

    func requestPermission() async -> Bool {
        configureCategories()
        return await withCheckedContinuation { continuation in
            center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                continuation.resume(returning: granted)
            }
        }
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await withCheckedContinuation { continuation in
            center.getNotificationSettings { settings in
                continuation.resume(returning: settings.authorizationStatus)
            }
        }
    }

    func configureCategories() {
        let done = UNNotificationAction(
            identifier: ReminderNotificationAction.done.rawValue,
            title: "Fait",
            options: [.authenticationRequired]
        )
        let snooze15 = UNNotificationAction(identifier: ReminderNotificationAction.snooze15.rawValue, title: "Snooze 15 min")
        let snooze30 = UNNotificationAction(identifier: ReminderNotificationAction.snooze30.rawValue, title: "Snooze 30 min")
        let snooze60 = UNNotificationAction(identifier: ReminderNotificationAction.snooze60.rawValue, title: "Snooze 60 min")
        let category = UNNotificationCategory(
            identifier: categoryId,
            actions: [done, snooze15, snooze30, snooze60],
            intentIdentifiers: [],
            options: [.customDismissAction]
        )
        center.setNotificationCategories([category])
    }

    func scheduleReminder(_ reminder: Reminder) {
        ensureAuthorized { isAuthorized in
            guard isAuthorized else { return }
            ReminderNotificationScheduler.scheduleReminder(reminder)
        }
    }

    func scheduleSnooze(baseId: String, title: String, minutes: Int) {
        ensureAuthorized { [weak self] isAuthorized in
            guard let self, isAuthorized else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.sound = .default
            content.categoryIdentifier = self.categoryId
            content.userInfo = [
                self.reminderBaseIdKey: baseId,
                self.reminderTitleKey: title
            ]
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: TimeInterval(max(1, minutes) * 60), repeats: false)
            let request = UNNotificationRequest(
                identifier: "\(baseId)-snooze-\(minutes)-\(UUID().uuidString)",
                content: content,
                trigger: trigger
            )
            self.center.add(request)
            LocalAnalyticsService.track("snooze_used", metadata: ["minutes": "\(minutes)"])
        }
    }

    func cancelReminder(baseId: String) {
        ReminderNotificationScheduler.cancelReminder(baseId: baseId)
    }

    // Backward-compatible helpers used by protocol onboarding.
    func scheduleDailyReminder(id: String, title: String, hour: Int, minute: Int) {
        ensureAuthorized { [weak self] isAuthorized in
            guard let self, isAuthorized else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.sound = .default
            content.categoryIdentifier = self.categoryId
            content.userInfo = [
                self.reminderBaseIdKey: id,
                self.reminderTitleKey: title
            ]
            var date = DateComponents()
            date.hour = hour
            date.minute = minute
            let trigger = UNCalendarNotificationTrigger(dateMatching: date, repeats: true)
            let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
            self.center.add(request)
        }
    }

    func scheduleWeeklyReminder(id: String, title: String, hour: Int, minute: Int, weekday: Int) {
        ensureAuthorized { [weak self] isAuthorized in
            guard let self, isAuthorized else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.sound = .default
            content.categoryIdentifier = self.categoryId
            content.userInfo = [
                self.reminderBaseIdKey: id,
                self.reminderTitleKey: title
            ]
            var date = DateComponents()
            date.weekday = weekday
            date.hour = hour
            date.minute = minute
            let trigger = UNCalendarNotificationTrigger(dateMatching: date, repeats: true)
            let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
            self.center.add(request)
        }
    }
    
    func getAuthorizationStatus(completion: @escaping (UNAuthorizationStatus) -> Void) {
        center.getNotificationSettings { settings in
            completion(settings.authorizationStatus)
        }
    }
    
    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    func cancel(id: String) {
        center.removePendingNotificationRequests(withIdentifiers: [id])
    }

    func handleNotificationAction(identifier: String, userInfo: [AnyHashable: Any]) {
        guard let action = ReminderNotificationAction(rawValue: identifier) else { return }
        let baseId = (userInfo[reminderBaseIdKey] as? String) ?? ""
        let title = (userInfo[reminderTitleKey] as? String) ?? "Rappel AJUSTE"
        guard !baseId.isEmpty else { return }

        switch action {
        case .done:
            SharedStore.mutate { snapshot in
                guard let idx = snapshot.reminders.firstIndex(where: { $0.notificationBaseId == baseId }) else { return }
                snapshot.reminders[idx].enabled = false
            }
            cancelReminder(baseId: baseId)
            NotificationCenter.default.post(
                name: .bioTrackReminderMarkedDone,
                object: nil,
                userInfo: [NotificationService.reminderBaseIdUserInfoKey: baseId]
            )
            LocalAnalyticsService.track("reminder_done", metadata: ["baseId": baseId])
        case .snooze15, .snooze30, .snooze60:
            if let minutes = action.minutes {
                scheduleSnooze(baseId: baseId, title: title, minutes: minutes)
            }
        }
        WidgetRefresh.reloadAll()
    }

    private func ensureAuthorized(completion: @escaping (Bool) -> Void) {
        configureCategories()
        center.getNotificationSettings { [weak self] settings in
            guard let self else { return }
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral:
                completion(true)
            case .notDetermined:
                self.center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
                    completion(granted)
                }
            default:
                completion(false)
            }
        }
    }
}
