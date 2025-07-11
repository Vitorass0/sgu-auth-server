package br.edu.ufape.sguAuthService.comunicacao.controllers;


import br.edu.ufape.sguAuthService.comunicacao.dto.tecnico.TecnicoResponse;
import br.edu.ufape.sguAuthService.exceptions.notFoundExceptions.TecnicoNotFoundException;
import br.edu.ufape.sguAuthService.exceptions.notFoundExceptions.UsuarioNotFoundException;
import br.edu.ufape.sguAuthService.fachada.Fachada;
import br.edu.ufape.sguAuthService.models.Usuario;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/tecnico")
public class TecnicoController {
    private final Fachada fachada;
    private final ModelMapper modelMapper;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'GESTOR')")
    @GetMapping
    public Page<TecnicoResponse> listarTecnicos(@PageableDefault(sort = "id") Pageable pageable) {
        return fachada.listarTecnicos(pageable)
                .map(usuario -> new TecnicoResponse(usuario, modelMapper));
    }


    @GetMapping("/{id}")
    TecnicoResponse buscarTecnico(@PathVariable UUID id) throws TecnicoNotFoundException, UsuarioNotFoundException {
        return new TecnicoResponse(fachada.buscarTecnico(id), modelMapper);
    }

    @GetMapping("/current")
    ResponseEntity<TecnicoResponse> buscarTecnicoAtual() throws TecnicoNotFoundException, UsuarioNotFoundException {
        Usuario response = fachada.buscarTecnicoAtual();
        return new ResponseEntity<>(new TecnicoResponse(response, modelMapper), HttpStatus.OK);
    }

}
