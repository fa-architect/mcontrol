import SwiftUI
import Combine
import CoreBluetooth

// MARK: - BLE送信

final class BroadcastController: NSObject, ObservableObject, CBPeripheralManagerDelegate {

    @Published var isReady = false
    private var manager: CBPeripheralManager?

    // 固定の前置き（先頭4個）
    private let head: [String] = ["08F9", "2349", "CBAE", "D1C1"]

    // 末尾の7個（動作には不要だが、公式アプリと同じ形にそろえる）
    private let tail: [String] = ["0D0C", "0F0E", "1110", "1312", "1514", "1716", "1918"]

    // グループ 0=全体, 1=伸縮, 2=振動
    // 各グループ index 0 = 停止, 1〜9 = パターン1〜9
    private let table: [[[String]]] = [
        // 全体（公式アプリから観測）
        [
            ["9C6E", "0B3D"], ["156F", "0B2C"], ["8E6C", "0B1E"], ["076D", "0B0F"],
            ["B86A", "0B7B"], ["316B", "0B6A"], ["AA68", "0B58"], ["2369", "0B49"],
            ["D466", "0BB1"], ["5D67", "0BA0"]
        ],
        // 伸縮 CH1（Android版の命令から計算）
        [
            ["1F5E", "0B0C"], ["965F", "0B1D"], ["0D5C", "0B2F"], ["845D", "0B3E"],
            ["3B5A", "0B4A"], ["B25B", "0B5B"], ["2958", "0B69"], ["A059", "0B78"],
            ["5756", "0B80"], ["DE57", "0B91"]
        ],
        // 振動 CH2（Android版の命令から計算）
        [
            ["982E", "0B7F"], ["112F", "0B6E"], ["8A2C", "0B5C"], ["032D", "0B4D"],
            ["BC2A", "0B39"], ["352B", "0B28"], ["AE28", "0B1A"], ["2729", "0B0B"],
            ["D026", "0BF3"], ["5927", "0BE2"]
        ]
    ]

    override init() {
        super.init()
        let options: [String: Any] = [
            CBPeripheralManagerOptionShowPowerAlertKey: true
        ]
        manager = CBPeripheralManager(delegate: self, queue: nil, options: options)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        isReady = (peripheral.state == .poweredOn)
    }

    func send(group: Int, index: Int) {
        guard isReady, let mgr = manager else { return }
        mgr.stopAdvertising()
        let list: [String] = head + table[group][index] + tail
        let uuids: [CBUUID] = list.map { CBUUID(string: $0) }
        mgr.startAdvertising([CBAdvertisementDataServiceUUIDsKey: uuids])
    }
}

// MARK: - ボタン

struct PatternButton: View {
    let number: Int
    let isSelected: Bool
    let accent: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("\(number)")
                .font(.title3)
                .bold()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .foregroundColor(isSelected ? accent : Color.primary)
                .background(isSelected ? accent.opacity(0.15) : Color.gray.opacity(0.12))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isSelected ? accent : Color.gray.opacity(0.3),
                                lineWidth: isSelected ? 1.5 : 0.5)
                )
        }
    }
}

// MARK: - 1つのグループ（全体・伸縮・振動）

struct GroupCard: View {
    let title: String
    let accent: Color
    let selected: Int
    let onPattern: (Int) -> Void
    let onStop: () -> Void

    private let columns: [GridItem] = [
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8),
        GridItem(.flexible(), spacing: 8)
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Rectangle()
                    .fill(accent)
                    .frame(width: 4, height: 18)
                    .cornerRadius(2)
                Text(title)
                    .font(.headline)
                    .foregroundColor(accent)
                Spacer()
                Text(selected == 0 ? "停止中" : "パターン \(selected)")
                    .font(.caption)
                    .bold()
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(accent.opacity(0.12))
                    .foregroundColor(accent)
                    .cornerRadius(10)
            }

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(1...9, id: \.self) { n in
                    PatternButton(number: n, isSelected: selected == n, accent: accent) {
                        onPattern(n)
                    }
                }
            }

            Button(action: onStop) {
                Text("\(title)を停止")
                    .font(.subheadline)
                    .bold()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(accent.opacity(0.1))
                    .foregroundColor(accent)
                    .cornerRadius(8)
            }
        }
        .padding(12)
        .background(Color.gray.opacity(0.06))
        .cornerRadius(12)
    }
}

// MARK: - 画面

struct ContentView: View {
    @StateObject private var ble = BroadcastController()
    @State private var allSel: Int = 0
    @State private var ch1Sel: Int = 0
    @State private var ch2Sel: Int = 0

    private let purple = Color(red: 0.35, green: 0.25, blue: 0.55)
    private let blue = Color(red: 0.05, green: 0.27, blue: 0.49)
    private let pink = Color(red: 0.72, green: 0.20, blue: 0.40)

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Text("MControl")
                    .font(.title)
                    .bold()
                    .padding(.top, 16)

                Text(ble.isReady ? "準備完了" : "Bluetooth準備中…")
                    .font(.caption)
                    .foregroundColor(.gray)

                GroupCard(title: "全体", accent: purple, selected: allSel,
                          onPattern: { n in tapAll(n) },
                          onStop: { stopAll() })

                GroupCard(title: "伸縮", accent: blue, selected: ch1Sel,
                          onPattern: { n in tapCh1(n) },
                          onStop: { stopCh1() })

                GroupCard(title: "振動", accent: pink, selected: ch2Sel,
                          onPattern: { n in tapCh2(n) },
                          onStop: { stopCh2() })

                Button(action: stopAll) {
                    Text("■ 全停止")
                        .font(.title3)
                        .bold()
                        .frame(maxWidth: .infinity)
                        .padding()
                        .foregroundColor(Color(red: 0.47, green: 0.12, blue: 0.12))
                        .background(Color(red: 0.99, green: 0.92, blue: 0.92))
                        .cornerRadius(10)
                }
                .padding(.bottom, 24)
            }
            .padding(.horizontal)
        }
    }

    // 全体：両方のチャンネルを同じパターンにする
    private func tapAll(_ n: Int) {
        if allSel == n { stopAll(); return }
        allSel = n
        ch1Sel = n
        ch2Sel = n
        ble.send(group: 0, index: n)
    }

    private func stopAll() {
        allSel = 0
        ch1Sel = 0
        ch2Sel = 0
        ble.send(group: 0, index: 0)
    }

    // 伸縮だけ
    private func tapCh1(_ n: Int) {
        if ch1Sel == n { stopCh1(); return }
        ch1Sel = n
        allSel = 0
        ble.send(group: 1, index: n)
    }

    private func stopCh1() {
        ch1Sel = 0
        allSel = 0
        ble.send(group: 1, index: 0)
    }

    // 振動だけ
    private func tapCh2(_ n: Int) {
        if ch2Sel == n { stopCh2(); return }
        ch2Sel = n
        allSel = 0
        ble.send(group: 2, index: n)
    }

    private func stopCh2() {
        ch2Sel = 0
        allSel = 0
        ble.send(group: 2, index: 0)
    }
}
