package com.lysenko.Payments.controller;

import com.lysenko.Payments.model.entity.account.Account;
import com.lysenko.Payments.model.entity.account.Status;
import com.lysenko.Payments.model.entity.card.Card;
import com.lysenko.Payments.model.entity.payment.Payment;
import com.lysenko.Payments.model.entity.user.User;
import com.lysenko.Payments.model.repository.*;
import com.lysenko.Payments.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Controller
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final int PAGE_SIZE = 3;

    @Autowired
    AccountRepository accountRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CardRepository cardRepository;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RequestUnblockRepository requestUnblockRepository;
    @Autowired
    UserService userService;

    @GetMapping("/user")

    public String getUserPage(Model model,
                              @RequestParam("page") Optional<Integer> pageOptional,
                              @RequestParam("sortBy") Optional<String> sortByOptional,
                              @RequestParam("sortOrder") Optional<String> sortOrderOptional) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepository.findUserByEmail(userDetails.getUsername());

        int page = pageOptional.orElse(0);
        String sortBy = sortByOptional.orElse("id");
        String sortOrder = sortOrderOptional.orElse("DESC");

        final PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE, Sort.Direction.fromString(sortOrder), sortBy);
        final Page<Account> accountPage = accountRepository.findAccountsByUserId(user.getUserId(), pageRequest);
        final List<Account> accounts = accountPage.getContent();
        final int numberOfPages = accountPage.getTotalPages();
        model.addAttribute("accounts", accounts);
        model.addAttribute("numberOfPages", numberOfPages);
        model.addAttribute("page", page);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);
        log.debug("Return user.jsp");
        return "user";
    }

    @GetMapping("/account")
    public String getAccountPage(Model model, @RequestParam("id") int id,
                                 @RequestParam("page") Optional<Integer> pageOptional,
                                 @RequestParam("sortOrder") Optional<String> sortOrderOptional,
                                 @RequestParam("sortBy") Optional<String> sortByOptional) {
        int page = pageOptional.orElse(0);
        String sortBy = sortByOptional.orElse("id");
        String sortOrder = sortOrderOptional.orElse("DESC");

        final List<Card> card = cardRepository.findCardByAccountId(id);
        model.addAttribute("cards", card);
        final PageRequest pageRequest = PageRequest.of(page, PAGE_SIZE, Sort.Direction.fromString(sortOrder), sortBy);
        final Page<Payment> paymentPage = paymentRepository.findPaymentsByAccountId(id, pageRequest);
        final List<Payment> payments = paymentPage.getContent();
        final int numberOfPages = paymentPage.getTotalPages();
        model.addAttribute("payments", payments);
        model.addAttribute("id", id);
        model.addAttribute("page", page);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("numberOfPages", numberOfPages);
        final Double balance = accountRepository.findAccountById(id).getBalance();
        model.addAttribute("balance", balance);
        return "account";
    }

    @PostMapping("/top_up")
    public String topUpAccount(@RequestParam("total") double total, @RequestParam("accountId") int id) {
        Account account = accountRepository.findAccountById(id);
        account.setBalance(account.getBalance() + total);
        accountRepository.save(account);
        return "redirect:/account?id=" + id;
    }

    @GetMapping("/add_account")
    public String addNewUserAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepository.findUserByEmail(userDetails.getUsername());
        userService.createNewAccount(user.getUserId());
        return "redirect:/user";
    }

    @GetMapping("/payment/new")
    public String newPayment(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final UserDetails userDetails = (UserDetails) auth.getPrincipal();
        User user = userRepository.findUserByEmail(userDetails.getUsername());
        log.debug("try to get accounts where status is OPEN ");
        List<Account> accounts = accountRepository.findAccountByUserIdAndStatus(user.getUserId(), Status.OPEN);
        log.debug("accounts: " + accounts);
        model.addAttribute("accounts", accounts);
        return "new";
    }

    @PostMapping("/payment/create")
    public String createPayment(@RequestParam("total") double total, @RequestParam("accountId") int id) {
        Date date = new Date();
        Account account = accountRepository.getById(id);
        if (account.getBalance() >= total) {
            account.setBalance(account.getBalance() - total);
            paymentRepository.createNewPayment(total, id, date);

            return "redirect:/account?page=0&id=" + id;
        } else {
            return "redirect:/payment/new?error=true";
        }
    }

    @GetMapping("/block")
    public String toBlockAccount(@RequestParam("id") int accountId) {
        log.debug("try to found account");
        Account account = accountRepository.findAccountById(accountId);
        log.debug("change account status" + account);
        account.setStatus(Status.BLOCKED);
        log.debug("saving account with changed status");
        accountRepository.save(account);
        return "redirect:/user";
    }

    @GetMapping("/sent-request")
    public String toSentRequestUnblockAccount(@RequestParam("id") int accountId) {
        log.debug("insert new request");
        requestUnblockRepository.createRequestUnblock(accountId);
        return "redirect:/user";
    }
}
