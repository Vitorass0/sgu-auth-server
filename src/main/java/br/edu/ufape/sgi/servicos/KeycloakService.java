package br.edu.ufape.sgi.servicos;



import br.edu.ufape.sgi.comunicacao.dto.auth.TokenResponse;
import br.edu.ufape.sgi.exceptions.auth.KeycloakAuthenticationException;
import br.edu.ufape.sgi.servicos.interfaces.KeycloakServiceInterface;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service @RequiredArgsConstructor
public class KeycloakService implements KeycloakServiceInterface {
    private Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.auth-server-url}")
    private String keycloakServerUrl;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${keycloak.client-id}")
    private String clientId;


    @Override
    @PostConstruct
    public void init() {
        System.out.println(keycloakServerUrl);
        // Inicialize o cliente Keycloak
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(keycloakServerUrl) // URL do servidor Keycloak
                .realm("master") // Realm do admin
                .clientId("admin-cli")
                .username("admin") // Credenciais do administrador
                .password("admin")
                .build();
    }

    @Override
    public TokenResponse login(String email, String password) throws KeycloakAuthenticationException {
        String tokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        // Fazer a requisição HTTP para o token endpoint do Keycloak
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // Parâmetros da requisição
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", email);
        formData.add("password", password);

        // Fazer a requisição
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);
        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, TokenResponse.class);
            // Verificar se a requisição foi bem sucedida e retornar o token adicionando as roles no response
            if (response.getStatusCode() == HttpStatus.OK) {
                TokenResponse tokenResponse = response.getBody();
                String userId = getUserId(email);
                List<RoleRepresentation> roles = keycloak.realm(realm).users().get(userId).roles().realmLevel().listEffective();
                assert tokenResponse != null;
                tokenResponse.setRoles(roles.stream().map(RoleRepresentation::getName).toList());
                return tokenResponse;
            }
            // Retorno de status diferente de OK
            throw new KeycloakAuthenticationException("Erro ao autenticar no Keycloak: " + response.getStatusCode());

        } catch (HttpStatusCodeException e) {
            // Captura qualquer erro HTTP e retorna uma mensagem apropriada com base no status
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new KeycloakAuthenticationException("Credenciais inválidas. Verifique o email e a senha.");
            }
            throw new KeycloakAuthenticationException("Erro ao autenticar no Keycloak: " + e.getStatusCode(), e);

        } catch (ResourceAccessException e) {
            // Captura erros de conexão ou timeout
            throw new KeycloakAuthenticationException("Não foi possível acessar o servidor Keycloak. Verifique sua conexão.", e);

        } catch (Exception e) {
            // Captura qualquer outro erro inesperado
            throw new KeycloakAuthenticationException("Erro inesperado durante o login.", e);
        }
    }

    @Override
    public TokenResponse refreshToken(String refreshToken) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        String keycloakTokenUrl = keycloakServerUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(keycloakTokenUrl, request, TokenResponse.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody(); // Retorna o novo access_token e refresh_token
        } else {
            throw new RuntimeException("Failed to refresh token");
        }
    }


    @Override
    public void createUser(String email, String password, String role) throws  KeycloakAuthenticationException {
        try {
            // Configurar as credenciais do usuário
            UserRepresentation user = KeycloakServiceInterface.getUserRepresentation(email, password);

            // Criar o usuário no Keycloak
            Response response = keycloak.realm(realm).users().create(user);

            // Verificar se a criação foi bem-sucedida
            if (response.getStatus() != 201) {
                if (response.getStatus() == 409) {
                    throw new KeycloakAuthenticationException("Credenciais já existentes. Tente outro email.");
                }
                throw new KeycloakAuthenticationException("Erro ao criar o usuário no Keycloak. Status: " + response.getStatus());
            }

            // Atribuir o papel (role) ao usuário
            String userId = keycloak.realm(realm).users().search(email).getFirst().getId();
            RoleRepresentation userRole = keycloak.realm(realm).roles().get(role).toRepresentation();
            keycloak.realm(realm).users().get(userId).roles().realmLevel().add(Collections.singletonList(userRole));

        } catch (NotFoundException e) {
            throw new KeycloakAuthenticationException("Role " + role + " não encontrado no Keycloak.", e);

        } catch (KeycloakAuthenticationException e) {
            throw e;  // Exceção já personalizada, não precisa de novo tratamento

        } catch (Exception e) {
            // Captura de qualquer outro erro inesperado
            throw new KeycloakAuthenticationException("Erro inesperado ao criar o usuário no Keycloak.", e);
        }
    }

    @Override
    public void deleteUser(String userId) {
        keycloak.realm(realm).users().get(userId).remove();
    }



    @Override
    public String getUserId(String username) {
        List<UserRepresentation> user = keycloak.realm(realm).users().search(username, true);
        if (!user.isEmpty()) {
            return user.getFirst().getId();
        }
        throw new IndexOutOfBoundsException("User not found");
    }
}