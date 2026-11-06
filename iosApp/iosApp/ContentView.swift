import SwiftUI
import UIKit
import ComposeAppSpike

/// Embrulha o UIViewController do Compose (MainViewControllerKt.MainViewController())
/// num SwiftUI View via UIViewControllerRepresentable.
struct ComposeViewControllerRepresentable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeViewControllerRepresentable()
            .ignoresSafeArea(.all)
    }
}
