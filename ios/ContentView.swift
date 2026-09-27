import SwiftUI
import Combine
import CoreBluetooth

// MARK: - BLE送信

final class BroadcastController: NSObject, ObservableObject, CBPeripheralManagerDelegate {

    @Published var isReady = false
    private var manager: CBPeripheralManager?

    // 固定UUID前半（D1C1まで）
    private let head: [String] = ["08F9", "2349", "CBAE", "D1C1"]

    // 固定UUID後半（D1C1以降）
    private let tail: [String] = ["0D0C", "0F0E", "1110", "1312", "1514", "1716", "1918"]

    // index 0 = 停止, 1〜7 = パターン1〜7
    private let variable: [[String]] = [
        ["9C6E", "0B3D"],
        ["156F", "0B2C"],
        ["8E6C", "0B1E"],
        ["076D", "0B0F"],
        ["B86A", "0B7B"],
        ["316B", "0B6A"],
        ["AA68", "0B58"],
        ["9C6E", "0B3D"]
    ]

    override init() {
        super.init()
        let options: [String: Any] = [
            CBPeripheralManagerOptionShowPowerAlertKey: true
        ]
        manager = CBPeripheralManager(delegate: self, queue: nil, options: options)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("Bluetooth state: \(peripheral.state.rawValue)")
        isReady = (peripheral.state == .poweredOn)
    }

    func send(_ index: Int) {
        guard isReady, let mgr = manager else { return }
        mgr.stopAdvertising()
        // 順番：head + 可変UUID + tail
        let list: [String] = head + variable[index] + tail
        let uuids: [CBUUID] = list.map { CBUUID(string: $0) }
        mgr.startAdvertising([CBAdvertisementDataServiceUUIDsKey: uuids])
    }
}

// MARK: - ボタン

struct PatternButton: View {
    let number: Int
    let isSelected: Bool
    let action: () -> Void

    private let accent = Color(red: 0.05, green: 0.27, blue: 0.49)

    var body: some View {
        Button(action: action) {
            Text("\(number)")
                .font(.title2)
                .bold()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
                .foregroundColor(isSelected ? accent : Color.primary)
                .background(isSelected ? accent.opacity(0.15) : Color.gray.opacity(0.12))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isSelected ? accent : Color.gray.opacity(0.3),
                                lineWidth: isSelected ? 1.5 : 0.5)
                )
        }
    }
}

// MARK: - 画面

struct ContentView: View {
    @StateObject private var ble = BroadcastController()
    @State private var selected: Int = 0

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]

    var body: some View {
        VStack(spacing: 16) {
            Text("MControl")
                .font(.title)
                .bold()
                .padding(.top, 40)

            Text(statusText)
                .font(.subheadline)
                .foregroundColor(.gray)

            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(1...7, id: \.self) { n in
                    PatternButton(number: n, isSelected: selected == n) {
                        tap(n)
                    }
                }
            }
            .padding(.horizontal)

            Button(action: stop) {
                Text("■ 停止")
                    .font(.title3)
                    .bold()
                    .frame(maxWidth: .infinity)
                    .padding()
                    .foregroundColor(Color(red: 0.47, green: 0.12, blue: 0.12))
                    .background(Color(red: 0.99, green: 0.92, blue: 0.92))
                    .cornerRadius(10)
            }
            .padding(.horizontal)

            Spacer()
        }
    }

    private var statusText: String {
        if !ble.isReady { return "Bluetooth準備中…" }
        return selected == 0 ? "停止中" : "パターン \(selected) 送信中"
    }

    private func tap(_ n: Int) {
        if selected == n { stop() } else { selected = n; ble.send(n) }
    }

    private func stop() {
        selected = 0
        ble.send(0)
    }
}
