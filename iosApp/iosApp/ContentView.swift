import SwiftUI
import UIKit
import Shared

/// Embrulha o UIViewController do Compose (MainViewControllerKt.MainViewController(),
/// exportado pelo framework `Shared` do :shared) num SwiftUI View.
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
