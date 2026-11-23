import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly auth = inject(AuthService);

  protected readonly failed = inject(ActivatedRoute).snapshot.queryParamMap.has('error');

  protected login(): void {
    this.auth.login();
  }
}
