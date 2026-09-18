package br.pucminas.eduka.backend.controller;

import br.pucminas.eduka.backend.service.AlunoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/aluno")
public class AlunoController {

    @Autowired
    private AlunoService alunoService;

    @RequestMapping("/{id}")
    public AlunoService findById(@PathVariable("id") Long id) {
        alunoService.findById(id);
    }

    public AlunoService findAll(){
        alunoService.findAll();
    }




}
