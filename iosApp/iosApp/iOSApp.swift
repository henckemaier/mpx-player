import SwiftUI

// Host SwiftUI mínimo do porte iOS (P7 F7.1). Fullscreen, ignorando a safe area, para a
// tela-fumaça do Compose Multiplatform ir até a borda no screenshot do CI.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
