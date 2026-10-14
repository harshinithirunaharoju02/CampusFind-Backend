package com.campusfind.controller;

import com.campusfind.entity.User;
import com.campusfind.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:5174")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public User registerUser(@RequestBody User user) {
        return userService.saveUser(user);
    }

    @PostMapping("/login")
    public User loginUser(@RequestBody User user) {

        Optional<User> existingUser =
                userService.getUserByEmail(user.getEmail());

        if (existingUser.isPresent()
                && existingUser.get().getPassword().equals(user.getPassword())) {

            return existingUser.get();
        }

        return null;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    public Optional<User> getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}