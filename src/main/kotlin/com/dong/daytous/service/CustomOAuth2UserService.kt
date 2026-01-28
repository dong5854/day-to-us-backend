package com.dong.daytous.service

import com.dong.daytous.domain.user.Role
import com.dong.daytous.domain.user.User
import com.dong.daytous.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository
) : DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val attributes = oAuth2User.attributes

        val email = attributes["email"] as String
        val name = attributes["name"] as String
        val provider = userRequest.clientRegistration.registrationId
        val providerId = attributes["sub"] as String

        val user = userRepository.findByEmail(email)
            .map {
                it.name = name
                it
            }
            .orElseGet {
                User(
                    name = name,
                    email = email,
                    role = Role.USER,
                    provider = provider,
                    providerId = providerId
                )
            }

        userRepository.save(user)

        return oAuth2User
    }
}
