import SwiftUI

// Host SwiftUI mínimo do spike CMP (Fase 3). Fullscreen, ignorando a safe area, para
// as scanlines/efeitos CRT irem até a borda no screenshot do CI.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
