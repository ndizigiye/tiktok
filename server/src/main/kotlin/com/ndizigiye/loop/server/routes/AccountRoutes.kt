package com.ndizigiye.loop.server.routes

import com.ndizigiye.loop.api.LoginRequest
import com.ndizigiye.loop.api.RegisterRequest
import com.ndizigiye.loop.api.UpdateProfileRequest
import com.ndizigiye.loop.server.LoopServices
import com.ndizigiye.loop.server.media.MediaKind
import com.ndizigiye.loop.server.media.UploadSpec
import com.ndizigiye.loop.server.media.receiveUpload
import com.ndizigiye.loop.server.plugins.JWT_AUTH
import com.ndizigiye.loop.server.plugins.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Registration, login and the signed-in user's own profile. */
fun Route.accountRoutes(services: LoopServices) {
    route("/api/auth") {
        post("/register") {
            call.respond(HttpStatusCode.Created, services.users.register(call.receive<RegisterRequest>()))
        }
        post("/login") {
            call.respond(services.users.login(call.receive<LoginRequest>()))
        }
    }
    authenticate(JWT_AUTH) {
        route("/api/me") {
            get {
                call.respond(services.users.me(call.userId()))
            }
            patch {
                call.respond(services.users.updateProfile(call.userId(), call.receive<UpdateProfileRequest>()))
            }
            post("/avatar") {
                val userId = call.userId()
                val upload = call.receiveUpload(UploadSpec.AVATAR) { services.storage.newFile(MediaKind.AVATARS, it) }
                call.respond(services.uploads.replaceAvatar(userId, upload))
            }
            get("/bookmarks") {
                call.respond(services.videos.bookmarks(call.userId(), call.pageRequest()))
            }
        }
    }
}
