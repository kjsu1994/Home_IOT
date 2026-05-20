package com.homeiot.auth.oauth2;

import com.homeiot.member.entity.Member;
import com.homeiot.member.service.MemberService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final MemberService memberService;

    public CustomOAuth2UserService(MemberService memberService) {
        this.memberService = memberService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User delegateUser = new DefaultOAuth2UserService().loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthProfile profile = OAuthProfile.from(registrationId, delegateUser.getAttributes());
        Member member = memberService.findOrCreateOauthMember(profile.email(), profile.name(), profile.provider());
        var authorities = member.getRoles().stream().map(SimpleGrantedAuthority::new).toList();
        return new DefaultOAuth2User(authorities, delegateUser.getAttributes(), profile.nameAttributeKey());
    }

    private record OAuthProfile(String email, String name, String nameAttributeKey, Member.AuthProvider provider) {
        static OAuthProfile from(String registrationId, Map<String, Object> attributes) {
            if ("naver".equals(registrationId)) {
                Object rawResponse = attributes.get("response");
                if (!(rawResponse instanceof Map<?, ?> response)) {
                    throw new OAuth2AuthenticationException("invalid naver profile response");
                }
                return new OAuthProfile(
                        String.valueOf(response.get("email")),
                        String.valueOf(response.get("name")),
                        "response",
                        Member.AuthProvider.NAVER
                );
            }
            return new OAuthProfile(
                    String.valueOf(attributes.get("email")),
                    String.valueOf(attributes.get("name")),
                    "sub",
                    Member.AuthProvider.GOOGLE
            );
        }
    }
}
